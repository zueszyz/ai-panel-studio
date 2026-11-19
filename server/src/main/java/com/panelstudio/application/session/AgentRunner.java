package com.panelstudio.application.session;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.panelstudio.api.dto.StreamEvents.AgentStatus;import com.panelstudio.api.dto.StreamEvents.MessageDelta;
import com.panelstudio.api.dto.StreamEvents.MessageEnd;
import com.panelstudio.api.dto.StreamEvents.MessageFailed;
import com.panelstudio.api.dto.StreamEvents.MessageStart;
import com.panelstudio.domain.entity.AgentEntity;
import com.panelstudio.domain.entity.AgentRunEntity;
import com.panelstudio.domain.entity.MessageEntity;
import com.panelstudio.domain.enums.AgentRunPhase;
import com.panelstudio.domain.enums.AgentRunTrigger;
import com.panelstudio.domain.enums.MessageKind;
import com.panelstudio.domain.enums.MessageRole;
import com.panelstudio.domain.enums.MessageStatus;
import com.panelstudio.domain.model.ChatMessage;
import com.panelstudio.domain.support.IdPrefixes;
import com.panelstudio.domain.support.Timestamps;
import com.panelstudio.domain.support.UlidGenerator;
import com.panelstudio.infra.config.AppProperties;
import com.panelstudio.infra.llm.CancelToken;
import com.panelstudio.infra.llm.LlmClient;
import com.panelstudio.infra.llm.LlmException;
import com.panelstudio.infra.llm.LlmResult;
import com.panelstudio.infra.llm.StreamListener;
import com.panelstudio.infra.mapper.AgentRunMapper;
import com.panelstudio.infra.mapper.MessageMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 单个席位的一次发言：装配上下文 → 流式调用 → 合并 delta 推送 → 落库最终态 + 轨迹。
 *
 * <p>delta 不落库（rules/30 §2），只在结束时一次 UPDATE；轨迹在结束时一次 INSERT。
 * 单条失败绝不向上抛，由调用方决定"其他人继续"。</p>
 */
@Component
public class AgentRunner {

    private static final Logger log = LoggerFactory.getLogger(AgentRunner.class);
    /** delta 合并窗口（rules/30 §2：60–80ms）。 */
    private static final long FLUSH_INTERVAL_MS = 70L;

    private final LlmClient llmClient;
    private final ContextBuilder contextBuilder;
    private final MessageMapper messageMapper;
    private final AgentRunMapper runMapper;
    private final EventPublisher publisher;
    private final AppProperties properties;
    private final UlidGenerator idGenerator;
    private final com.panelstudio.application.MetricsService metrics;

    public AgentRunner(LlmClient llmClient,
                       ContextBuilder contextBuilder,
                       MessageMapper messageMapper,
                       AgentRunMapper runMapper,
                       EventPublisher publisher,
                       AppProperties properties,
                       UlidGenerator idGenerator,
                       com.panelstudio.application.MetricsService metrics) {
        this.llmClient = llmClient;
        this.contextBuilder = contextBuilder;
        this.messageMapper = messageMapper;
        this.runMapper = runMapper;
        this.publisher = publisher;
        this.properties = properties;
        this.idGenerator = idGenerator;
        this.metrics = metrics;
    }

    /**
     * @param messageId 可预分配（/start 需要在生成前就返回 openingMessageId），为空时内部生成
     * @param retry     单条重试：沿用同一 messageId 更新已有行，而不是再插一条
     */
    public record RunRequest(String discussionId,
                             String topic,
                             String goal,
                             AgentEntity seat,
                             String messageId,
                             int roundNo,
                             MessageKind kind,
                             AgentRunTrigger trigger,
                             MessageEntity latestUser,
                             String quoteMessageId,
                             String quoteContent,
                             List<MessageEntity> history,
                             Map<String, String> seatNames,
                             boolean opening,
                             boolean digest,
                             boolean retry) {
    }

    /**
     * @return 落库后的消息（content 为最终文本；失败时为已收到的部分内容）
     */
    public MessageEntity run(RunRequest request, CancelToken token) {
        AgentEntity seat = request.seat();
        String messageId = request.messageId() == null
                ? idGenerator.nextWithPrefix(IdPrefixes.MESSAGE)
                : request.messageId();
        long startedAt = System.currentTimeMillis();
        String startedIso = Timestamps.now();

        MessageEntity message = new MessageEntity();
        message.setId(messageId);
        message.setDiscussionId(request.discussionId());
        message.setAgentId(seat.getId());
        message.setRole(seat.getRoleType() == com.panelstudio.domain.enums.AgentRoleType.HOST
                ? MessageRole.HOST : MessageRole.EXPERT);
        message.setKind(request.kind());
        message.setStatus(MessageStatus.STREAMING);
        message.setRoundNo(request.roundNo());
        message.setSeq(0);
        message.setContent("");
        message.setQuoteMessageId(request.quoteMessageId());
        message.setIsInterrupt(0);
        message.setTokensIn(0);
        message.setTokensOut(0);
        message.setAttempt(1);
        message.setClientMsgId("run-" + messageId);
        if (request.retry()) {
            // 重试不新增行：把同一张卡片改回 STREAMING，attempt+1（前端凭 messageId 原位刷新）
            messageMapper.update(null, Wrappers.<MessageEntity>lambdaUpdate()
                    .eq(MessageEntity::getId, messageId)
                    .set(MessageEntity::getStatus, MessageStatus.STREAMING)
                    .set(MessageEntity::getContent, "")
                    .setSql("attempt = attempt + 1")
                    .set(MessageEntity::getUpdatedAt, Timestamps.now()));
        } else {
            messageMapper.insert(message);
        }

        String discussionId = request.discussionId();
        publisher.publish(discussionId, "agent.status", new AgentStatus(seat.getId(), "thinking"));
        publisher.publish(discussionId, "message.start", new MessageStart(messageId, seat.getId(),
                message.getRole().name(), request.kind().name(), request.roundNo(), request.quoteMessageId()));

        List<ChatMessage> context = contextBuilder.build(new ContextBuilder.ContextRequest(
                seat, request.topic(), request.goal(), request.history(), request.seatNames(),
                request.latestUser(), request.quoteContent(), request.opening(), request.digest()));

        StringBuilder pending = new StringBuilder();
        StringBuilder all = new StringBuilder();
        AtomicInteger seq = new AtomicInteger();
        long[] lastFlush = {startedAt};
        StreamListener listener = new StreamListener() {
            @Override
            public void onFirstToken(long latencyMs) {
                metrics.recordFirstToken(latencyMs);
                publisher.publish(discussionId, "agent.status", new AgentStatus(seat.getId(), "generating"));
            }

            @Override
            public void onDelta(String text) {
                pending.append(text);
                all.append(text);
                long now = System.currentTimeMillis();
                if (now - lastFlush[0] >= FLUSH_INTERVAL_MS) {
                    flush();
                    lastFlush[0] = now;
                }
            }

            private void flush() {
                if (pending.isEmpty()) {
                    return;
                }
                String text = pending.toString();
                pending.setLength(0);
                publisher.publish(discussionId, "message.delta",
                        new MessageDelta(messageId, seq.incrementAndGet(), text));
            }
        };

        try {
            LlmResult result = llmClient.chatStream(context, listener, token);
            flushRemaining(discussionId, messageId, pending, seq);
            metrics.agentCall();

            int tokensOut = result.tokensOut();
            int latency = (int) (System.currentTimeMillis() - startedAt);
            markDone(message, result.content(), seq.get(), tokensOut, latency);
            publisher.publish(discussionId, "message.end",
                    new MessageEnd(messageId, result.content(), tokensOut, latency, MessageStatus.DONE.name()));
            publisher.publish(discussionId, "agent.status", new AgentStatus(seat.getId(), "done"));
            recordRun(request, messageId, AgentRunPhase.DONE, null, latency, tokensOut, startedIso,
                    request.quoteMessageId());
            message.setContent(result.content());
            message.setTokensOut(tokensOut);
            message.setLatencyMs(latency);
            message.setStatus(MessageStatus.DONE);
            return message;
        } catch (LlmException ex) {
            flushRemaining(discussionId, messageId, pending, seq);
            int latency = (int) (System.currentTimeMillis() - startedAt);
            String partial = all.toString();
            if ("CANCELLED".equals(ex.code())) {
                markPartial(message, partial, seq.get(), latency);
                recordRun(request, messageId, AgentRunPhase.FAILED, ex.code(), latency, 0, startedIso,
                        request.quoteMessageId());
                log.info("agent.cancel discussionId={} seatId={} messageId={}", discussionId, seat.getId(), messageId);
                return message;
            }
            metrics.agentFailure();
            markFailed(message, partial, seq.get(), latency);
            publisher.publish(discussionId, "message.failed",
                    new MessageFailed(messageId, ex.code(), ex.retriable()));
            publisher.publish(discussionId, "agent.status", new AgentStatus(seat.getId(), "failed"));
            recordRun(request, messageId, AgentRunPhase.FAILED, ex.code(), latency, 0, startedIso,
                    request.quoteMessageId());
            log.warn("agent.fail discussionId={} seatId={} code={} partialChars={}",
                    discussionId, seat.getId(), ex.code(), partial.length());
            return message;
        }
    }

    private void flushRemaining(String discussionId, String messageId, StringBuilder pending, AtomicInteger seq) {
        if (pending.isEmpty()) {
            return;
        }
        String text = pending.toString();
        pending.setLength(0);
        publisher.publish(discussionId, "message.delta", new MessageDelta(messageId, seq.incrementAndGet(), text));
    }

    private void markDone(MessageEntity message, String content, int seq, int tokensOut, int latency) {
        MessageEntity patch = new MessageEntity();
        patch.setId(message.getId());
        patch.setContent(content);
        patch.setSeq(seq);
        patch.setTokensOut(tokensOut);
        patch.setLatencyMs(latency);
        patch.setStatus(MessageStatus.DONE);
        messageMapper.updateById(patch);
    }

    private void markPartial(MessageEntity message, String partial, int seq, int latency) {
        MessageEntity patch = new MessageEntity();
        patch.setId(message.getId());
        patch.setContent(partial);
        patch.setSeq(seq);
        patch.setLatencyMs(latency);
        patch.setStatus(MessageStatus.PARTIAL);
        messageMapper.updateById(patch);
        message.setContent(partial);
        message.setStatus(MessageStatus.PARTIAL);
    }

    private void markFailed(MessageEntity message, String partial, int seq, int latency) {
        MessageEntity patch = new MessageEntity();
        patch.setId(message.getId());
        patch.setContent(partial);
        patch.setSeq(seq);
        patch.setLatencyMs(latency);
        patch.setStatus(MessageStatus.FAILED);
        messageMapper.updateById(patch);
        message.setContent(partial);
        message.setStatus(MessageStatus.FAILED);
    }

    private void recordRun(RunRequest request, String messageId, AgentRunPhase phase, String errorCode,
                           int latency, int tokens, String startedAt, String quoteMessageId) {
        AgentRunEntity run = new AgentRunEntity();
        run.setId(idGenerator.nextWithPrefix(IdPrefixes.RUN));
        run.setDiscussionId(request.discussionId());
        run.setAgentId(request.seat().getId());
        run.setMessageId(messageId);
        run.setPhase(phase);
        run.setTriggerType(request.trigger());
        run.setRoundNo(request.roundNo());
        run.setStatus(phase == AgentRunPhase.DONE ? "DONE" : "FAILED");
        run.setErrorCode(errorCode);
        run.setDurationMs(latency);
        run.setTokens(tokens);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("model", properties.getLlm().getModel());
        if (quoteMessageId != null) {
            meta.put("quotes", List.of(quoteMessageId));
        }
        meta.put("kind", request.kind().name());
        run.setMeta(meta);
        run.setStartedAt(startedAt);
        run.setEndedAt(Timestamps.now());
        runMapper.insert(run);
    }

}
