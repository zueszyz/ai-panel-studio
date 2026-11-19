package com.panelstudio.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.panelstudio.api.advice.ApiException;
import com.panelstudio.api.dto.DiscussionDtos.ClaimResponse;
import com.panelstudio.api.dto.DiscussionDtos.CreatePanelRequest;
import com.panelstudio.api.dto.DiscussionDtos.SeatView;
import com.panelstudio.api.dto.PanelEvents.Analysis;
import com.panelstudio.api.dto.PanelEvents.Done;
import com.panelstudio.api.dto.PanelEvents.Failed;
import com.panelstudio.domain.entity.AgentEntity;
import com.panelstudio.domain.entity.DiscussionEntity;
import com.panelstudio.domain.entity.ParticipantEntity;
import com.panelstudio.domain.enums.AgentRoleType;
import com.panelstudio.domain.enums.AgentStatus;
import com.panelstudio.domain.enums.DiscussionStatus;
import com.panelstudio.domain.enums.ParticipantRole;
import com.panelstudio.domain.model.SeatDraft;
import com.panelstudio.domain.model.TopicAnalysis;
import com.panelstudio.domain.support.IdPrefixes;
import com.panelstudio.domain.support.UlidGenerator;
import com.panelstudio.infra.config.AsyncConfig;
import com.panelstudio.infra.config.AppProperties;
import com.panelstudio.infra.llm.LlmClient;
import com.panelstudio.infra.llm.LlmException;
import com.panelstudio.infra.llm.PromptTemplates;
import com.panelstudio.infra.mapper.AgentMapper;
import com.panelstudio.infra.mapper.ParticipantMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 组局：议题分析 + 席位逐个生成（SSE 逐席推送）+ 认领/撤销。
 * 生成在 agentExecutor 上跑，请求线程立即返回 emitter；写库在最后一次性事务里完成。
 */
@Service
public class PanelService {

    private static final Logger log = LoggerFactory.getLogger(PanelService.class);

    private static final Set<DiscussionStatus> EDITABLE =
            EnumSet.of(DiscussionStatus.DRAFT, DiscussionStatus.PANEL_READY, DiscussionStatus.FAILED);
    private static final List<AgentRoleType> EXPERT_ROLES = List.of(
            AgentRoleType.STRATEGIST, AgentRoleType.ANALYST, AgentRoleType.TECH,
            AgentRoleType.FINANCE, AgentRoleType.RESEARCHER);

    private final DiscussionService discussionService;
    private final PanelPersistService persistService;
    private final PanelParser panelParser;
    private final LlmClient llmClient;
    private final AppProperties properties;
    private final ThreadPoolTaskExecutor agentExecutor;
    private final AgentMapper agentMapper;
    private final ParticipantMapper participantMapper;
    private final UserService userService;
    private final UlidGenerator idGenerator;

    public PanelService(DiscussionService discussionService,
                        PanelPersistService persistService,
                        PanelParser panelParser,
                        LlmClient llmClient,
                        AppProperties properties,
                        @Qualifier(AsyncConfig.AGENT_EXECUTOR) ThreadPoolTaskExecutor agentExecutor,
                        AgentMapper agentMapper,
                        ParticipantMapper participantMapper,
                        UserService userService,
                        UlidGenerator idGenerator) {
        this.discussionService = discussionService;
        this.persistService = persistService;
        this.panelParser = panelParser;
        this.llmClient = llmClient;
        this.properties = properties;
        this.agentExecutor = agentExecutor;
        this.agentMapper = agentMapper;
        this.participantMapper = participantMapper;
        this.userService = userService;
        this.idGenerator = idGenerator;
    }

    // ── 席位生成 ────────────────────────────────────────────

    public SseEmitter generate(String discussionId, CreatePanelRequest request) {
        DiscussionEntity discussion = discussionService.requireDiscussion(discussionId);
        if (!EDITABLE.contains(discussion.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "DISCUSSION_STATE_INVALID",
                    "这场圆桌已经开始，不能再重组席位");
        }
        if (request.regenerateRequested()
                && discussion.getRegenCount() != null
                && discussion.getRegenCount() >= properties.getPanel().getRegenLimit()) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "REGEN_LIMIT",
                    "换一批次数已用完（最多 " + properties.getPanel().getRegenLimit() + " 次）");
        }
        SseEmitter emitter = new SseEmitter(0L);
        agentExecutor.execute(() -> runGeneration(emitter, discussionId, request));
        return emitter;
    }

    private void runGeneration(SseEmitter emitter, String discussionId, CreatePanelRequest request) {
        try {
            DiscussionEntity discussion = discussionService.requireDiscussion(discussionId);
            List<AgentEntity> existing = agentMapper.findByDiscussionId(discussionId);
            Set<String> claimedSeatIds = participantMapper.findClaimedSeats(discussionId).stream()
                    .map(ParticipantEntity::getSeatId)
                    .collect(Collectors.toCollection(HashSet::new));

            boolean idempotent = !request.regenerateRequested() && !request.presetRequested()
                    && discussion.getStatus() == DiscussionStatus.PANEL_READY
                    && existing.size() == discussion.getPanelSize();
            if (idempotent) {
                pushExistingPanel(emitter, discussion, existing, claimedSeatIds);
                return;
            }

            TopicAnalysis analysis = resolveAnalysis(discussion, request);
            send(emitter, "panel.analysis", new Analysis(analysis.getGoal(), analysis.getDisputes(),
                    analysis.getAngles(), analysis.getAudience()));

            List<AgentEntity> claimed = existing.stream()
                    .filter(seat -> claimedSeatIds.contains(seat.getId()))
                    .toList();
            for (AgentEntity seat : claimed) {
                send(emitter, "seat.added", toSeatView(seat, true));
            }
            Set<Integer> occupied = claimed.stream().map(AgentEntity::getOrderIndex)
                    .collect(Collectors.toSet());
            List<SeatDraft> knownDrafts = claimed.stream().map(this::toDraft).collect(Collectors.toList());

            int panelSize = discussion.getPanelSize();
            List<PanelPersistService.NewSeat> created = new ArrayList<>();
            boolean preset = request.presetRequested();
            List<SeatDraft> presetSeats = preset ? PresetPanels.seats(discussion.getTopic(), panelSize) : List.of();

            int expertSequence = 0;
            for (int index = 0; index < panelSize; index++) {
                if (occupied.contains(index)) {
                    continue;
                }
                boolean hostSeat = index == 0;
                SeatDraft draft;
                if (preset) {
                    draft = presetSeats.get(Math.min(index, presetSeats.size() - 1));
                } else {
                    AgentRoleType roleType = hostSeat
                            ? AgentRoleType.HOST
                            : EXPERT_ROLES.get(Math.min(expertSequence, EXPERT_ROLES.size() - 1));
                    draft = generateSeat(discussion.getTopic(), index, panelSize, hostSeat, roleType, knownDrafts);
                }
                expertSequence++;
                knownDrafts.add(draft);
                String seatId = idGenerator.nextWithPrefix(IdPrefixes.SEAT);
                created.add(new PanelPersistService.NewSeat(seatId, index, draft));
                send(emitter, "seat.added", toSeatView(seatId, index, draft, false));
            }

            List<String> toDelete = existing.stream()
                    .filter(seat -> !claimedSeatIds.contains(seat.getId()))
                    .map(AgentEntity::getId)
                    .toList();
            persistService.replacePanel(discussionId, analysis.getGoal(), analysis, created, toDelete,
                    request.regenerateRequested());
            send(emitter, "panel.done", new Done(discussionId, DiscussionStatus.PANEL_READY, panelSize,
                    preset ? "PRESET" : null));
            emitter.complete();
        } catch (ClientGoneException ex) {
            log.debug("组局流客户端已断开，停止推送");
            emitter.complete();
        } catch (LlmException ex) {
            log.warn("席位生成失败 code={}", ex.code());
            sendFailed(emitter, ex.code(), ex.getMessage(), ex.retriable());
        } catch (ApiException ex) {
            sendFailed(emitter, ex.code(), ex.getMessage(), false);
        } catch (RuntimeException ex) {
            log.error("席位生成异常", ex);
            sendFailed(emitter, "INTERNAL_ERROR", "生成席位时出错，请重试", true);
        }
    }

    private void pushExistingPanel(SseEmitter emitter, DiscussionEntity discussion,
                                   List<AgentEntity> seats, Set<String> claimedSeatIds) {
        TopicAnalysis analysis = discussion.getTopicAnalysis();
        if (analysis == null) {
            analysis = PresetPanels.analysis(discussion.getTopic());
        }
        send(emitter, "panel.analysis", new Analysis(analysis.getGoal(), analysis.getDisputes(),
                analysis.getAngles(), analysis.getAudience()));
        for (AgentEntity seat : seats) {
            send(emitter, "seat.added", toSeatView(seat, claimedSeatIds.contains(seat.getId())));
        }
        send(emitter, "panel.done", new Done(discussion.getId(), discussion.getStatus(), seats.size(), null));
        emitter.complete();
    }

    private TopicAnalysis resolveAnalysis(DiscussionEntity discussion, CreatePanelRequest request) {
        if (request.presetRequested()) {
            return PresetPanels.analysis(discussion.getTopic());
        }
        if (request.regenerateRequested() && discussion.getTopicAnalysis() != null) {
            return discussion.getTopicAnalysis();
        }
        // 未配置外部模型时 LlmClient 自动委托到内置离线模型，无需在这里拦截。
        String raw = llmClient.chatJson(PromptTemplates.analysisSystem(),
                PromptTemplates.analysisUser(discussion.getTopic(), discussion.getPanelSize())).content();
        return panelParser.parseAnalysis(raw);
    }

    /** 生成一席：结构不合法时带错误重问一次，仍失败抛 LLM_JSON_INVALID。 */
    private SeatDraft generateSeat(String topic, int index, int panelSize, boolean hostSeat,
                                   AgentRoleType roleType, List<SeatDraft> previous) {
        String systemPrompt = PromptTemplates.seatSystem();
        String userPrompt = PromptTemplates.seatUser(topic, index, panelSize, hostSeat, previous);
        String raw = llmClient.chatJson(systemPrompt, userPrompt).content();
        SeatDraft draft;
        try {
            draft = panelParser.parseSeat(raw);
        } catch (SeatValidationException first) {
            log.debug("席位 {} 校验失败，重问一次: {}", index, first.getMessage());
            String retryRaw = llmClient.chatJson(systemPrompt,
                    PromptTemplates.seatRepairUser(topic, index, first.getMessage())).content();
            try {
                draft = panelParser.parseSeat(retryRaw);
            } catch (SeatValidationException second) {
                throw LlmException.jsonInvalid();
            }
        }
        draft.setRoleType(roleType);
        return draft;
    }

    // ── 认领 / 撤销 ─────────────────────────────────────────

    @Transactional
    public ClaimResponse claim(String discussionId, String seatId, String deviceId) {
        DiscussionEntity discussion = discussionService.requireDiscussion(discussionId);
        if (discussion.getStatus() == DiscussionStatus.CLOSED
                || discussion.getStatus() == DiscussionStatus.SUMMARIZING) {
            throw new ApiException(HttpStatus.CONFLICT, "DISCUSSION_CLOSED", "这场圆桌已结束");
        }
        String userId = userService.resolveUserId(deviceId);
        if (userId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "缺少设备标识",
                    Map.of("field", "X-Device-Id"));
        }
        ParticipantEntity mine = participantMapper.findByDiscussionAndUser(discussionId, userId);

        if (seatId == null || seatId.isBlank()) {
            revoke(discussionId, mine);
            return new ClaimResponse(null, "VIEWER", "旁听者", AgentStatus.ACTIVE);
        }

        AgentEntity seat = agentMapper.selectById(seatId);
        if (seat == null || !discussionId.equals(seat.getDiscussionId())
                || seat.getStatus() == AgentStatus.RETIRED) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SEAT_NOT_FOUND", "这个席位不存在");
        }
        if (seat.getRoleType() == AgentRoleType.HOST) {
            throw new ApiException(HttpStatus.CONFLICT, "SEAT_NOT_CLAIMABLE", "主持人席位不可认领");
        }
        if (mine != null && seatId.equals(mine.getSeatId())) {
            return new ClaimResponse(seatId, "PLAYER", "你", seat.getStatus());
        }
        boolean takenByOther = participantMapper.findClaimedSeats(discussionId).stream()
                .anyMatch(participant -> seatId.equals(participant.getSeatId())
                        && !userId.equals(participant.getUserId()));
        if (takenByOther) {
            throw new ApiException(HttpStatus.CONFLICT, "SEAT_TAKEN", "该席位已被其他参与者认领",
                    Map.of("seatId", seatId));
        }

        if (mine != null) {
            revoke(discussionId, mine);
        }
        updateSeatStatus(seat, AgentStatus.REPLACED_BY_USER);
        ParticipantEntity participant = new ParticipantEntity();
        participant.setId(idGenerator.nextWithPrefix(IdPrefixes.PARTICIPANT));
        participant.setDiscussionId(discussionId);
        participant.setUserId(userId);
        participant.setSeatId(seatId);
        participant.setRole(ParticipantRole.PLAYER);
        participantMapper.insert(participant);
        return new ClaimResponse(seatId, "PLAYER", "你", AgentStatus.REPLACED_BY_USER);
    }

    private void revoke(String discussionId, ParticipantEntity mine) {
        if (mine == null) {
            return;
        }
        if (mine.getSeatId() != null) {
            AgentEntity seat = agentMapper.selectById(mine.getSeatId());
            if (seat != null) {
                updateSeatStatus(seat, AgentStatus.ACTIVE);
            }
        }
        participantMapper.deleteById(mine.getId());
    }

    /**
     * 只改状态：不能用 updateById(patch) —— patch 的 focus 是空 List 而非 null，
     * MyBatis-Plus 会把它当成「要更新的字段」写回 '[]'，认领/撤销就会把关注点冲掉。
     */
    private void updateSeatStatus(AgentEntity seat, AgentStatus status) {
        agentMapper.update(null, Wrappers.<AgentEntity>lambdaUpdate()
                .eq(AgentEntity::getId, seat.getId())
                .set(AgentEntity::getStatus, status));
    }

    // ── 事件与映射 ─────────────────────────────────────────

    private void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException ex) {
            throw new ClientGoneException();
        }
    }

    private void sendFailed(SseEmitter emitter, String code, String message, boolean retriable) {
        try {
            emitter.send(SseEmitter.event().name("panel.failed")
                    .data(new Failed(code, message, retriable, true), MediaType.APPLICATION_JSON));
            emitter.complete();
        } catch (IOException | IllegalStateException ignored) {
            emitter.complete();
        }
    }

    private SeatView toSeatView(AgentEntity seat, boolean claimedByMe) {
        return new SeatView(seat.getId(), seat.getRoleType(), seat.getName(), seat.getTitle(),
                seat.getStance(), seat.getFocus(), seat.getVoice(), seat.getColorToken(),
                seat.getOrderIndex(), seat.getStatus(), claimedByMe);
    }

    private SeatView toSeatView(String seatId, int orderIndex, SeatDraft draft, boolean claimedByMe) {
        return new SeatView(seatId, draft.getRoleType(), draft.getName(), draft.getTitle(),
                draft.getStance(), draft.getFocus(), draft.getVoice(), draft.getColorToken(),
                orderIndex, AgentStatus.ACTIVE, claimedByMe);
    }

    private SeatDraft toDraft(AgentEntity seat) {
        SeatDraft draft = new SeatDraft();
        draft.setName(seat.getName());
        draft.setTitle(seat.getTitle());
        draft.setStance(seat.getStance());
        draft.setFocus(seat.getFocus());
        draft.setVoice(seat.getVoice());
        draft.setColorToken(seat.getColorToken());
        draft.setRoleType(seat.getRoleType());
        return draft;
    }

    /** 组局流写失败（客户端已断开）时用于中止流程，不当作错误上报。 */
    private static final class ClientGoneException extends RuntimeException {
    }
}
