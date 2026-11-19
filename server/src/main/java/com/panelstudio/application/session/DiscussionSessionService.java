package com.panelstudio.application.session;

import com.panelstudio.api.advice.ApiException;
import com.panelstudio.api.dto.DiscussionDtos.SeatView;
import com.panelstudio.api.dto.SessionDtos.EndResponse;
import com.panelstudio.api.dto.SessionDtos.PostMessageRequest;
import com.panelstudio.api.dto.SessionDtos.PostMessageResponse;
import com.panelstudio.api.dto.SessionDtos.RetryMessageResponse;
import com.panelstudio.api.dto.SessionDtos.RunsResponse;
import com.panelstudio.api.dto.SessionDtos.StartResponse;
import com.panelstudio.api.dto.SessionDtos.TurnResponse;
import com.panelstudio.api.dto.StreamEvents.DiscussionClosed;
import com.panelstudio.api.dto.StreamEvents.DiscussionUpdated;
import com.panelstudio.api.dto.StreamEvents.MessageEnd;
import com.panelstudio.api.dto.StreamEvents.MessageStart;
import com.panelstudio.api.dto.StreamEvents.MessageView;
import com.panelstudio.api.dto.StreamEvents.RunView;
import com.panelstudio.api.dto.StreamEvents.Snapshot;
import com.panelstudio.application.DiscussionService;
import com.panelstudio.application.MetricsService;
import com.panelstudio.application.UserService;
import com.panelstudio.domain.entity.AgentEntity;
import com.panelstudio.domain.entity.AgentRunEntity;
import com.panelstudio.domain.entity.DiscussionEntity;
import com.panelstudio.domain.entity.MessageEntity;
import com.panelstudio.domain.entity.ParticipantEntity;
import com.panelstudio.domain.enums.DiscussionStatus;
import com.panelstudio.domain.enums.MessageKind;
import com.panelstudio.domain.enums.MessageRole;
import com.panelstudio.domain.enums.MessageStatus;
import com.panelstudio.domain.statemachine.DiscussionStateMachine;
import com.panelstudio.domain.statemachine.IllegalStateTransitionException;
import com.panelstudio.domain.support.IdPrefixes;
import com.panelstudio.domain.support.Timestamps;
import com.panelstudio.domain.support.UlidGenerator;
import com.panelstudio.infra.mapper.AgentMapper;
import com.panelstudio.infra.mapper.AgentRunMapper;
import com.panelstudio.infra.mapper.DiscussionMapper;
import com.panelstudio.infra.mapper.MessageMapper;
import com.panelstudio.infra.mapper.ParticipantMapper;
import com.panelstudio.infra.sse.SseRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 讨论内核的用例层：开场 / 发言 / 推进回合 / 结束 / 事件流 / 轨迹。
 *
 * <p>Controller 只做参数绑定，所有状态判断与调度入口都在这里；
 * 真正的生成由 {@link SessionActor} 串行调度、{@link AgentRunner} 并行执行。</p>
 */
@Service
public class DiscussionSessionService {

    private static final Logger log = LoggerFactory.getLogger(DiscussionSessionService.class);
    private static final int RUNS_LIMIT = 200;
    private static final int QUOTE_PREVIEW = 40;
    private static final long SSE_CLOSE_DELAY_MS = 800L;

    private final DiscussionService discussionService;
    private final SessionActorRegistry actorRegistry;
    private final SessionStore sessionStore;
    private final SseRegistry sseRegistry;
    private final EventPublisher publisher;
    private final MessageMapper messageMapper;
    private final AgentMapper agentMapper;
    private final AgentRunMapper runMapper;
    private final ParticipantMapper participantMapper;
    private final DiscussionMapper discussionMapper;
    private final UserService userService;
    private final UlidGenerator idGenerator;
    private final MetricsService metrics;

    public DiscussionSessionService(DiscussionService discussionService,
                                    SessionActorRegistry actorRegistry,
                                    SessionStore sessionStore,
                                    SseRegistry sseRegistry,
                                    EventPublisher publisher,
                                    MessageMapper messageMapper,
                                    AgentMapper agentMapper,
                                    AgentRunMapper runMapper,
                                    ParticipantMapper participantMapper,
                                    DiscussionMapper discussionMapper,
                                    UserService userService,
                                    UlidGenerator idGenerator,
                                    MetricsService metrics) {
        this.discussionService = discussionService;
        this.actorRegistry = actorRegistry;
        this.sessionStore = sessionStore;
        this.sseRegistry = sseRegistry;
        this.publisher = publisher;
        this.messageMapper = messageMapper;
        this.agentMapper = agentMapper;
        this.runMapper = runMapper;
        this.participantMapper = participantMapper;
        this.discussionMapper = discussionMapper;
        this.userService = userService;
        this.idGenerator = idGenerator;
        this.metrics = metrics;
    }

    // ── 开场 ────────────────────────────────────────────────

    public StartResponse start(String discussionId) {
        DiscussionEntity discussion = discussionService.requireDiscussion(discussionId);
        DiscussionStatus current = discussion.getStatus();
        if (current == DiscussionStatus.LIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_STARTED", "这场圆桌已经开场了");
        }
        if (current == DiscussionStatus.SUMMARIZING || current == DiscussionStatus.CLOSED) {
            throw new ApiException(HttpStatus.CONFLICT, "DISCUSSION_CLOSED", "这场圆桌已结束");
        }
        if (current == DiscussionStatus.DRAFT || current == DiscussionStatus.FAILED) {
            throw new ApiException(HttpStatus.CONFLICT, "DISCUSSION_NOT_LIVE", "席位还没生成完，不能开场");
        }

        boolean resuming = current == DiscussionStatus.INTERRUPTED;
        // 先确认有席位再迁移状态：否则席位缺失时状态已被改成 LIVE，用户退不回去
        List<AgentEntity> seats = agentMapper.findByDiscussionId(discussionId);
        if (seats.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "DISCUSSION_NOT_LIVE", "这场圆桌还没有席位");
        }
        transit(discussion, DiscussionStatus.LIVE);

        SessionActor actor = actorRegistry.getOrCreate(discussion, seats);
        metrics.sessionStarted();

        if (resuming) {
            actor.resume();
            publisher.publish(discussionId, "discussion.updated",
                    new DiscussionUpdated("LIVE", actor.turnIndex(), actor.budget().usagePercent()));
            return new StartResponse("LIVE", actor.turnIndex(), null);
        }
        String openingMessageId = actor.startOpening();
        return new StartResponse("LIVE", 0, openingMessageId);
    }

    // ── 用户发言 ────────────────────────────────────────────

    public PostMessageResponse postMessage(String discussionId, PostMessageRequest request, String deviceId) {
        DiscussionEntity discussion = discussionService.requireDiscussion(discussionId);
        if (discussion.getStatus() == DiscussionStatus.CLOSED
                || discussion.getStatus() == DiscussionStatus.SUMMARIZING) {
            throw new ApiException(HttpStatus.GONE, "DISCUSSION_CLOSED", "这场圆桌已结束");
        }
        if (discussion.getStatus() != DiscussionStatus.LIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "DISCUSSION_NOT_LIVE", "这场圆桌还没进入讨论");
        }

        MessageEntity existing = messageMapper.findByClientMsgId(discussionId, request.clientMsgId());
        if (existing != null) {
            return new PostMessageResponse(existing.getId(), existing.getStatus().name(), false, 0);
        }

        SessionActor actor = actorRegistry.get(discussionId);
        if (actor == null) {
            actor = actorRegistry.getOrCreate(discussion, agentMapper.findByDiscussionId(discussionId));
        }
        if (!actor.budget().canSpeak()) {
            throw new ApiException(HttpStatus.PAYMENT_REQUIRED, "BUDGET_EXHAUSTED",
                    "本场 token 预算已用完，请结束讨论并生成纪要");
        }

        String userId = userService.resolveUserId(deviceId);
        MessageEntity message = new MessageEntity();
        message.setId(idGenerator.nextWithPrefix(IdPrefixes.MESSAGE));
        message.setDiscussionId(discussionId);
        message.setUserId(userId);
        message.setRole(MessageRole.USER);
        message.setKind(MessageKind.INTERRUPT);
        message.setStatus(MessageStatus.DONE);
        message.setRoundNo(discussion.getTurnIndex() == null ? 0 : discussion.getTurnIndex());
        message.setSeq(0);
        message.setContent(request.content().trim());
        message.setQuoteMessageId(blankToNull(request.quoteMessageId()));
        message.setIsInterrupt(1);
        message.setTokensIn(0);
        message.setTokensOut(0);
        message.setAttempt(1);
        message.setClientMsgId(request.clientMsgId());
        messageMapper.insert(message);

        publisher.publish(discussionId, "message.start", new MessageStart(message.getId(), null,
                MessageRole.USER.name(), MessageKind.INTERRUPT.name(), message.getRoundNo(),
                message.getQuoteMessageId()));
        publisher.publish(discussionId, "message.end", new MessageEnd(message.getId(), message.getContent(),
                0, 0L, MessageStatus.DONE.name()));

        String quotedSeatId = seatIdOfQuote(discussionId, message.getQuoteMessageId());
        List<String> speakers = actor.onUserMessage(message, quotedSeatId);
        log.info("user.message discussionId={} messageId={} responders={}",
                discussionId, message.getId(), speakers.size());
        return new PostMessageResponse(message.getId(), message.getStatus().name(), true, speakers.size());
    }

    public TurnResponse nextTurn(String discussionId) {
        DiscussionEntity discussion = discussionService.requireDiscussion(discussionId);
        if (discussion.getStatus() != DiscussionStatus.LIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "DISCUSSION_NOT_LIVE", "这场圆桌不在讨论中");
        }
        SessionActor actor = actorRegistry.getOrCreate(discussion,
                agentMapper.findByDiscussionId(discussionId));
        List<String> speakers = actor.nextRound();
        return new TurnResponse(actor.turnIndex() + 1, speakers);
    }

    // ── 单条重试 ────────────────────────────────────────────

    public RetryMessageResponse retry(String discussionId, String messageId) {
        DiscussionEntity discussion = discussionService.requireDiscussion(discussionId);
        if (discussion.getStatus() != DiscussionStatus.LIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "DISCUSSION_NOT_LIVE", "这场圆桌不在讨论中");
        }
        MessageEntity message = messageMapper.selectById(messageId);
        if (message == null || !discussionId.equals(message.getDiscussionId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MESSAGE_NOT_FOUND", "这条发言不存在");
        }
        if (message.getAgentId() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "MESSAGE_NOT_RETRIABLE", "用户发言不需要重试");
        }
        SessionActor actor = actorRegistry.getOrCreate(discussion,
                agentMapper.findByDiscussionId(discussionId));
        if (!actor.retryMessage(message)) {
            throw new ApiException(HttpStatus.CONFLICT, "AGENT_BUSY", "这位嘉宾正在发言，稍后再试");
        }
        return new RetryMessageResponse(messageId, MessageStatus.STREAMING.name(),
                message.getAttempt() == null ? 2 : message.getAttempt() + 1);
    }

    // ── 结束 ────────────────────────────────────────────────

    public EndResponse end(String discussionId) {
        DiscussionEntity discussion = discussionService.requireDiscussion(discussionId);
        if (discussion.getStatus() == DiscussionStatus.CLOSED) {
            throw new ApiException(HttpStatus.CONFLICT, "DISCUSSION_CLOSED", "这场圆桌已结束");
        }
        DiscussionStatus target = discussion.getStatus() == DiscussionStatus.SUMMARIZING
                ? DiscussionStatus.SUMMARIZING
                : transit(discussion, DiscussionStatus.SUMMARIZING);
        String endedAt = Timestamps.now();
        sessionStore.updateStatus(discussionId, target, endedAt);

        SessionActor actor = actorRegistry.get(discussionId);
        if (actor != null) {
            actor.end();
        }
        metrics.sessionEnded();
        publisher.publish(discussionId, "discussion.closed", new DiscussionClosed(target.name(), endedAt));
        // 先让 closed 帧写出去，再关连接（PRD FR-8：结束后 3s 内关闭）
        CompletableFuture.delayedExecutor(SSE_CLOSE_DELAY_MS, TimeUnit.MILLISECONDS)
                .execute(() -> {
                    sseRegistry.closeAll(discussionId);
                    actorRegistry.remove(discussionId);
                });
        return new EndResponse(target.name(), endedAt);
    }

    // ── 事件流 ──────────────────────────────────────────────

    public SseEmitter stream(String discussionId, String deviceId, String lastEventId) {
        DiscussionEntity discussion = discussionService.requireDiscussion(discussionId);
        Long cursor = parseCursor(lastEventId);
        String userId = userService.resolveUserId(deviceId);
        return sseRegistry.register(discussionId, cursor, () -> snapshot(discussion, userId));
    }

    public Snapshot snapshot(DiscussionEntity discussion, String userId) {
        List<AgentEntity> seats = agentMapper.findByDiscussionId(discussion.getId());
        Map<String, AgentEntity> agentsById = new LinkedHashMap<>();
        seats.forEach(seat -> agentsById.put(seat.getId(), seat));
        List<MessageEntity> messages = messageMapper.findByDiscussionId(discussion.getId());
        List<SeatView> seatViews = discussionService.seatViews(discussion.getId(), userId);
        List<RunView> runs = runMapper.findByDiscussionId(discussion.getId(), RUNS_LIMIT).stream()
                .map(this::toRunView).toList();
        return new Snapshot(discussion.getId(), discussion.getTopic(), discussion.getGoal(),
                discussion.getStatus().name(),
                discussion.getTurnIndex() == null ? 0 : discussion.getTurnIndex(),
                mySeatId(discussion.getId(), userId), seatViews,
                toMessageViews(messages, agentsById), runs);
    }

    public RunsResponse runs(String discussionId) {
        discussionService.requireDiscussion(discussionId);
        List<RunView> items = runMapper.findByDiscussionId(discussionId, RUNS_LIMIT).stream()
                .map(this::toRunView).toList();
        return new RunsResponse(items);
    }

    // ── 视图装配 ────────────────────────────────────────────

    public List<MessageView> toMessageViews(List<MessageEntity> messages, Map<String, AgentEntity> agentsById) {
        Map<String, String> contentById = new HashMap<>();
        for (MessageEntity message : messages) {
            contentById.put(message.getId(), message.getContent());
        }
        List<MessageView> views = new ArrayList<>(messages.size());
        for (MessageEntity message : messages) {
            AgentEntity agent = message.getAgentId() == null ? null : agentsById.get(message.getAgentId());
            views.add(new MessageView(
                    message.getId(),
                    message.getAgentId(),
                    agent == null ? (message.getRole() == MessageRole.USER ? "你" : "主持人") : agent.getName(),
                    agent == null ? "emerald" : agent.getColorToken(),
                    message.getRole().name(),
                    message.getKind().name(),
                    message.getStatus().name(),
                    message.getRoundNo() == null ? 0 : message.getRoundNo(),
                    message.getContent() == null ? "" : message.getContent(),
                    message.getQuoteMessageId(),
                    quotePreview(message.getQuoteMessageId(), contentById),
                    message.getIsInterrupt() == null ? 0 : message.getIsInterrupt(),
                    message.getTokensOut() == null ? 0 : message.getTokensOut(),
                    message.getLatencyMs(),
                    message.getAttempt() == null ? 1 : message.getAttempt(),
                    message.getCreatedAt()));
        }
        return views;
    }

    private String quotePreview(String quoteMessageId, Map<String, String> contentById) {
        if (quoteMessageId == null) {
            return null;
        }
        String content = contentById.get(quoteMessageId);
        if (content == null) {
            return null;
        }
        String flat = content.replaceAll("\\s+", " ").trim();
        return flat.length() <= QUOTE_PREVIEW ? flat : flat.substring(0, QUOTE_PREVIEW) + "…";
    }

    private RunView toRunView(AgentRunEntity run) {
        return new RunView(run.getId(), run.getAgentId(), run.getMessageId(),
                run.getPhase() == null ? null : run.getPhase().name(),
                run.getTriggerType() == null ? null : run.getTriggerType().name(),
                run.getRoundNo() == null ? 0 : run.getRoundNo(),
                run.getDurationMs(), run.getTokens() == null ? 0 : run.getTokens(),
                run.getMeta() == null ? Map.of() : run.getMeta(),
                run.getStartedAt(), run.getEndedAt());
    }

    private String mySeatId(String discussionId, String userId) {
        if (userId == null) {
            return null;
        }
        ParticipantEntity participant = participantMapper.findByDiscussionAndUser(discussionId, userId);
        return participant == null ? null : participant.getSeatId();
    }

    /** 引用目标必须是本场消息，且只能是 AI 席位（引用用户消息不触发"回应席位"）。 */
    private String seatIdOfQuote(String discussionId, String quoteMessageId) {
        if (quoteMessageId == null) {
            return null;
        }
        MessageEntity quoted = messageMapper.selectById(quoteMessageId);
        if (quoted == null || !discussionId.equals(quoted.getDiscussionId())) {
            return null;
        }
        return quoted.getAgentId();
    }

    private DiscussionStatus transit(DiscussionEntity discussion, DiscussionStatus target) {
        try {
            DiscussionStatus next = DiscussionStateMachine.transit(discussion.getStatus(), target);
            DiscussionEntity patch = new DiscussionEntity();
            patch.setId(discussion.getId());
            patch.setStatus(next);
            if (target == DiscussionStatus.LIVE && discussion.getStartedAt() == null) {
                patch.setStartedAt(Timestamps.now());
            }
            discussionMapper.updateById(patch);
            discussion.setStatus(next);
            return next;
        } catch (IllegalStateTransitionException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "DISCUSSION_STATE_INVALID",
                    "当前状态不能这样做",
                    Map.of("from", discussion.getStatus().name(), "to", target.name()));
        }
    }

    private static Long parseCursor(String lastEventId) {
        if (lastEventId == null || lastEventId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(lastEventId.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
