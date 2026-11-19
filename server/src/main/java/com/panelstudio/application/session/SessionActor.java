package com.panelstudio.application.session;

import com.panelstudio.api.dto.StreamEvents.AgentStatus;
import com.panelstudio.api.dto.StreamEvents.DiscussionUpdated;
import com.panelstudio.api.dto.StreamEvents.ErrorFrame;
import com.panelstudio.api.dto.StreamEvents.TurnStart;
import com.panelstudio.domain.entity.AgentEntity;
import com.panelstudio.domain.entity.MessageEntity;
import com.panelstudio.domain.enums.AgentRoleType;
import com.panelstudio.domain.enums.AgentRunTrigger;
import com.panelstudio.domain.enums.DiscussionStatus;
import com.panelstudio.domain.enums.MessageKind;
import com.panelstudio.domain.policy.SpeakerPolicy;
import com.panelstudio.domain.support.IdPrefixes;
import com.panelstudio.domain.support.UlidGenerator;
import com.panelstudio.infra.config.AppProperties;
import com.panelstudio.infra.llm.CancelToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 一场圆桌的内存状态机（ARCHITECTURE §3.1）：
 * <b>串行消费队列保证回合决策一致，回合内并行 fan-out 保证不互相阻塞</b>。
 *
 * <p>关键约束：</p>
 * <ul>
 *   <li>inflight 以 seatId 为键，同一席位不会被并发调用两次；</li>
 *   <li>单条失败只影响自己（allOf + handle），不中断整组；</li>
 *   <li>两级闸门：本场 3 路、全局 20 路；</li>
 *   <li>取消（结束讨论）走 CancelToken，不打断已落库的消息。</li>
 * </ul>
 */
public final class SessionActor {

    private static final Logger log = LoggerFactory.getLogger(SessionActor.class);
    private static final int HISTORY_LIMIT = 30;
    private static final int GATE_WAIT_SECONDS = 30;

    private final String discussionId;
    private final String topic;
    private final String goal;
    private final List<AgentEntity> seats;
    private final AgentEntity host;
    private final Map<String, AgentEntity> seatById = new LinkedHashMap<>();
    private final Map<String, String> seatNames = new LinkedHashMap<>();
    private final Map<String, Integer> lastSpokeRound = new ConcurrentHashMap<>();
    private final Set<String> spokenThisRound = ConcurrentHashMap.newKeySet();
    private final Map<String, CancelToken> inflight = new ConcurrentHashMap<>();
    /** 插话重试次数（按 user messageId）：席位都忙时延后再答复，避免插话被静默丢弃。 */
    private final Map<String, Integer> interruptRetries = new ConcurrentHashMap<>();
    private final List<MessageEntity> history = Collections.synchronizedList(new ArrayList<>());
    private final Deque<Runnable> queue = new ConcurrentLinkedDeque<>();
    private final AtomicBoolean draining = new AtomicBoolean(false);
    private final AtomicReference<DiscussionStatus> status;
    private final AtomicInteger turnIndex;
    private final TokenBudget budget;
    private final SessionStore store;
    private final EventPublisher publisher;
    private final AgentRunner agentRunner;
    private final ThreadPoolTaskExecutor executor;
    private final UlidGenerator idGenerator;
    private final Semaphore roomSemaphore;
    private final Semaphore globalSemaphore;
    private final int speakerLimit;

    public SessionActor(String discussionId,
                        String topic,
                        String goal,
                        List<AgentEntity> seats,
                        DiscussionStatus status,
                        int turnIndex,
                        SessionStore store,
                        EventPublisher publisher,
                        AgentRunner agentRunner,
                        ThreadPoolTaskExecutor executor,
                        UlidGenerator idGenerator,
                        Semaphore roomSemaphore,
                        Semaphore globalSemaphore,
                        AppProperties properties,
                        List<MessageEntity> history) {
        this.discussionId = discussionId;
        this.topic = topic;
        this.goal = goal;
        this.seats = List.copyOf(seats);
        this.seats.forEach(seat -> {
            seatById.put(seat.getId(), seat);
            seatNames.put(seat.getId(), seat.getName());
        });
        this.host = seats.stream()
                .filter(seat -> seat.getRoleType() == AgentRoleType.HOST)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("这场圆桌没有主持人席位"));
        this.status = new AtomicReference<>(status);
        this.turnIndex = new AtomicInteger(turnIndex);
        this.store = store;
        this.publisher = publisher;
        this.agentRunner = agentRunner;
        this.executor = executor;
        this.idGenerator = idGenerator;
        this.roomSemaphore = roomSemaphore;
        this.globalSemaphore = globalSemaphore;
        this.speakerLimit = properties.getPanel().getSpeakerLimit();
        this.budget = TokenBudget.from(properties);
        if (history != null) {
            this.history.addAll(history.subList(Math.max(0, history.size() - HISTORY_LIMIT), history.size()));
        }
    }

    // ── 对外动作 ────────────────────────────────────────────

    /** 开场：返回预分配的 openingMessageId（生成是异步的，接口不等待）。 */
    public String startOpening() {
        String messageId = idGenerator.nextWithPrefix(IdPrefixes.MESSAGE);
        enqueue(() -> {
            runSeat(host, messageId, 0, MessageKind.OPENING, AgentRunTrigger.ROUND_START,
                    null, null, true, false, false, new CancelToken());
            if (isLive()) {
                List<String> speakers = pickSpeakers(null, null);
                enqueue(() -> runTurn(speakers, reasonOf(null, null), null, null));
            }
        });
        return messageId;
    }

    /** 用户插话：下一轮由相关专家优先回应（PRD FR-4 第 5 条）。 */
    public List<String> onUserMessage(MessageEntity user, String quotedSeatId) {
        List<String> speakers = pickSpeakers(user, quotedSeatId);
        enqueue(() -> runTurn(speakers, reasonOf(user, quotedSeatId), user, quotedSeatId));
        return speakers;
    }

    /** 手动推进一回合（前端「继续下一轮」）。 */
    public List<String> nextRound() {
        List<String> speakers = pickSpeakers(null, null);
        enqueue(() -> runTurn(speakers, reasonOf(null, null), null, null));
        return speakers;
    }

    /** 结束讨论：停调度、取消 inflight，剩余队列丢弃。 */
    public void end() {
        status.set(DiscussionStatus.SUMMARIZING);
        queue.clear();
        inflight.values().forEach(CancelToken::cancel);
        inflight.clear();
        publisher.publish(discussionId, "discussion.updated",
                new DiscussionUpdated(DiscussionStatus.SUMMARIZING.name(), turnIndex.get(), budget.usagePercent()));
    }

    public void markInterrupted() {
        status.set(DiscussionStatus.INTERRUPTED);
    }

    public void resume() {
        status.set(DiscussionStatus.LIVE);
    }

    // ── 回合执行 ────────────────────────────────────────────

    private void runTurn(List<String> speakers, String reason, MessageEntity user, String quotedSeatId) {
        if (!isLive()) {
            return;
        }
        if (!budget.canSpeak()) {
            publisher.publish(discussionId, "error",
                    new ErrorFrame("BUDGET_EXHAUSTED", "本场 token 预算已用完，请结束讨论并生成纪要", false));
            return;
        }
        // 插话不能丢（FR-4）：只要有一位目标席位还在发言，就整轮延后，
        // 否则部分席位被跳过会让"同时点名两位"只剩一个人回应。
        if (user != null && !speakers.isEmpty()
                && speakers.stream().anyMatch(inflight::containsKey)) {
            int attempt = interruptRetries.merge(user.getId(), 1, Integer::sum);
            if (attempt <= 3) {
                CompletableFuture.delayedExecutor(300L, TimeUnit.MILLISECONDS)
                        .execute(() -> enqueue(() -> runTurn(speakers, reason, user, quotedSeatId)));
                log.debug("插话 {} 有目标席位还在发言，第 {} 次延后响应", user.getId(), attempt);
            } else {
                publisher.publish(discussionId, "error", new ErrorFrame("AGENT_BUSY",
                        "嘉宾都还在发言，这条插话暂未得到回应，请稍后再发一次", true));
            }
            return;
        }
        interruptRetries.remove(user == null ? "" : user.getId());
        int round = turnIndex.incrementAndGet();
        spokenThisRound.clear();
        spokenThisRound.addAll(speakers);
        store.updateTurnIndex(discussionId, round);
        publishTurnStart(round, speakers, reason);
        publisher.publish(discussionId, "discussion.updated",
                new DiscussionUpdated(DiscussionStatus.LIVE.name(), round, budget.usagePercent()));

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (String seatId : speakers) {
            AgentEntity seat = seatById.get(seatId);
            if (seat == null) {
                continue;
            }
            CancelToken token = new CancelToken();
            if (inflight.putIfAbsent(seatId, token) != null) {
                log.debug("席位 {} 已在发言，跳过本轮", seatId);
                continue;
            }
            CompletableFuture<Void> future = CompletableFuture
                    .runAsync(() -> runInSlot(seat, round, user, quotedSeatId, token), executor)
                    .handle((ignored, error) -> {
                        if (error != null) {
                            log.warn("席位 {} 第 {} 回合生成异常: {}", seatId, round, error.toString());
                        }
                        return null;
                    });
            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .handle((ignored, error) -> null)
                .thenRun(() -> afterTurn(round));
    }

    /** 闸门内执行：保证单场最多 3 路、全局最多 20 路并发调用模型。 */
    private void runInSlot(AgentEntity seat, int round, MessageEntity user, String quotedSeatId,
                           CancelToken token) {
        boolean roomAcquired = false;
        boolean globalAcquired = false;
        try {
            try {
                roomAcquired = roomSemaphore.tryAcquire(GATE_WAIT_SECONDS, TimeUnit.SECONDS);
                globalAcquired = roomAcquired && globalSemaphore.tryAcquire(GATE_WAIT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            if (!roomAcquired || !globalAcquired) {
                publisher.publish(discussionId, "agent.status", new AgentStatus(seat.getId(), "failed"));
                publisher.publish(discussionId, "error",
                        new ErrorFrame("LLM_RATE_LIMIT", "并发已满，本轮这位嘉宾没能发言", true));
                return;
            }
            runSeat(seat, null, round, MessageKind.SPEECH,
                    triggerOf(user, quotedSeatId), user, quotedSeatId, false, false, false, token);
        } finally {
            if (globalAcquired) {
                globalSemaphore.release();
            }
            if (roomAcquired) {
                roomSemaphore.release();
            }
            inflight.remove(seat.getId(), token);
            lastSpokeRound.put(seat.getId(), round);
        }
    }

    private void afterTurn(int round) {
        if (!isLive()) {
            return;
        }
        if (round % 2 == 0) {
            enqueue(() -> runDigest(round));
        }
    }

    private void runDigest(int round) {
        if (!isLive()) {
            return;
        }
        CancelToken token = new CancelToken();
        inflight.put(host.getId(), token);
        try {
            // 收束消息的 id 必须与 digest.start 一致，否则前端点轨迹定位不到这条消息
            String messageId = idGenerator.nextWithPrefix(IdPrefixes.MESSAGE);
            publisher.publish(discussionId, "digest.start",
                    new com.panelstudio.api.dto.StreamEvents.DigestStart(messageId));
            runSeat(host, messageId, round, MessageKind.DIGEST, AgentRunTrigger.DIGEST,
                    null, null, false, true, false, token);
        } finally {
            inflight.remove(host.getId(), token);
        }
    }

    private void runSeat(AgentEntity seat, String messageId, int round, MessageKind kind,
                         AgentRunTrigger trigger, MessageEntity user, String quotedSeatId,
                         boolean opening, boolean digest, boolean retry, CancelToken token) {
        String quoteContent = quotedSeatId == null ? null : contentOf(quotedSeatId);
        MessageEntity produced = agentRunner.run(new AgentRunner.RunRequest(
                discussionId, topic, goal, seat, messageId, round, kind, trigger, user,
                quotedSeatId, quoteContent, snapshotHistory(), new LinkedHashMap<>(seatNames),
                opening, digest, retry), token);
        budget.consume(produced.getTokensOut() == null ? 0 : produced.getTokensOut());
        remember(produced);
        if (!budget.canSpeak()) {
            publisher.publish(discussionId, "error",
                    new ErrorFrame("BUDGET_EXHAUSTED", "本场 token 预算已用完，请结束讨论并生成纪要", false));
        }
    }

    /**
     * 单条重试（PRD FR-9）：沿用同一 messageId，前端卡片原位回到 STREAMING。
     * 同一席位正在发言时不排队，直接返回 false 让调用方回 409。
     *
     * @param target 由仓储层按 id 读出的原消息（不用内存 history，避免老消息被裁掉后重试不了）
     */
    public boolean retryMessage(MessageEntity target) {
        if (!isLive() || target == null || target.getAgentId() == null) {
            return false;
        }
        AgentEntity seat = seatById.get(target.getAgentId());
        if (seat == null) {
            return false;
        }
        CancelToken token = new CancelToken();
        if (inflight.putIfAbsent(seat.getId(), token) != null) {
            return false;
        }
        enqueue(() -> {
            try {
                runSeat(seat, target.getId(), target.getRoundNo() == null ? 0 : target.getRoundNo(),
                        target.getKind(), AgentRunTrigger.ROUND_START, null, target.getQuoteMessageId(),
                        target.getKind() == MessageKind.OPENING, target.getKind() == MessageKind.DIGEST,
                        true, token);
            } finally {
                inflight.remove(seat.getId(), token);
            }
        });
        return true;
    }

    // ── 策略与工具 ──────────────────────────────────────────

    private List<String> pickSpeakers(MessageEntity user, String quotedSeatId) {
        int round = turnIndex.get() + 1;
        List<SpeakerPolicy.Candidate> candidates = seats.stream()
                .filter(seat -> seat.getRoleType() != AgentRoleType.HOST)
                .map(seat -> new SpeakerPolicy.Candidate(seat.getId(), seat.getName(), seat.getFocus(),
                        lastSpokeRound.getOrDefault(seat.getId(), 0),
                        spokenThisRound.contains(seat.getId())))
                .toList();
        String text = user == null ? "" : user.getContent();
        List<String> picked = SpeakerPolicy.pick(candidates, text, quotedSeatId, round, speakerLimit)
                .stream().map(SpeakerPolicy.Decision::seatId).toList();
        if (picked.isEmpty()) {
            // 全都在本回合发过言（例如 6 人圆桌）：退化为最久未发言的 K 位
            picked = candidates.stream()
                    .sorted(Comparator.comparingInt(SpeakerPolicy.Candidate::lastSpokeRound))
                    .limit(Math.min(2, candidates.size()))
                    .map(SpeakerPolicy.Candidate::seatId)
                    .toList();
            spokenThisRound.clear();
        }
        return picked;
    }

    private String reasonOf(MessageEntity user, String quotedSeatId) {
        if (quotedSeatId != null) {
            return "回应用户引用";
        }
        return user != null ? "回应用户插话" : "主持人指派";
    }

    private AgentRunTrigger triggerOf(MessageEntity user, String quotedSeatId) {
        if (quotedSeatId != null) {
            return AgentRunTrigger.PEER_QUOTE;
        }
        return user != null ? AgentRunTrigger.USER_INTERRUPT : AgentRunTrigger.ROUND_START;
    }

    private void publishTurnStart(int round, List<String> speakers, String reason) {
        publisher.publish(discussionId, "turn.start", new TurnStart(round, speakers, reason));
    }

    private String contentOf(String messageId) {
        synchronized (history) {
            return history.stream()
                    .filter(message -> messageId.equals(message.getId()))
                    .map(MessageEntity::getContent)
                    .findFirst()
                    .orElse(null);
        }
    }

    private List<MessageEntity> snapshotHistory() {
        synchronized (history) {
            return new ArrayList<>(history);
        }
    }

    private void remember(MessageEntity message) {
        synchronized (history) {
            // 重试会产出同一个 id 的新内容：替换而不是追加，否则历史里会出现两条同 id 消息
            for (int index = 0; index < history.size(); index++) {
                if (history.get(index).getId().equals(message.getId())) {
                    history.set(index, message);
                    return;
                }
            }
            history.add(message);
            while (history.size() > HISTORY_LIMIT) {
                history.remove(0);
            }
        }
    }

    /**
     * 串行消费：同一时刻只有一个 job 在跑，保证"回合决策"不会交叉。
     * job 内部只提交任务不阻塞，因此队列很快回到空闲。
     */
    private void enqueue(Runnable job) {
        queue.addLast(job);
        drain();
    }

    private void drain() {
        if (!draining.compareAndSet(false, true)) {
            return;
        }
        executor.execute(() -> {
            try {
                Runnable job;
                while ((job = queue.pollFirst()) != null) {
                    try {
                        job.run();
                    } catch (RuntimeException ex) {
                        log.error("act job failed discussionId={}", discussionId, ex);
                    }
                }
            } finally {
                draining.set(false);
                if (!queue.isEmpty()) {
                    drain();
                }
            }
        });
    }

    private boolean isLive() {
        return status.get() == DiscussionStatus.LIVE;
    }

    // ── 只读视图 ────────────────────────────────────────────

    public String discussionId() {
        return discussionId;
    }

    public DiscussionStatus status() {
        return status.get();
    }

    public int turnIndex() {
        return turnIndex.get();
    }

    public TokenBudget budget() {
        return budget;
    }

    public List<AgentEntity> seats() {
        return seats;
    }

    public int inflightCount() {
        return inflight.size();
    }

    /** 测试用：等待队列与 inflight 都清空（最多等待 timeoutMs）。 */
    public boolean awaitIdle(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (queue.isEmpty() && !draining.get() && inflight.isEmpty()) {
                return true;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }
}
