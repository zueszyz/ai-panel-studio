package com.panelstudio.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.panelstudio.domain.entity.AgentEntity;
import com.panelstudio.domain.entity.ParticipantEntity;
import com.panelstudio.domain.enums.AgentStatus;
import com.panelstudio.domain.enums.DiscussionStatus;
import com.panelstudio.domain.enums.ParticipantRole;
import com.panelstudio.domain.model.SeatDraft;
import com.panelstudio.domain.model.TopicAnalysis;
import com.panelstudio.domain.support.IdPrefixes;
import com.panelstudio.domain.support.Timestamps;
import com.panelstudio.domain.support.UlidGenerator;
import com.panelstudio.infra.mapper.AgentMapper;
import com.panelstudio.infra.mapper.DiscussionMapper;
import com.panelstudio.infra.mapper.ParticipantMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 席位落库：一次性写入（要么整场成功，要么什么都不留），
 * 事务只包 DB 写，绝不包 LLM 调用（rules/40 §1 事务纪律）。
 */
@Service
public class PanelPersistService {

    private final AgentMapper agentMapper;
    private final DiscussionMapper discussionMapper;
    private final ParticipantMapper participantMapper;
    private final ObjectMapper objectMapper;
    private final UlidGenerator idGenerator;

    public PanelPersistService(AgentMapper agentMapper, DiscussionMapper discussionMapper,
                               ParticipantMapper participantMapper,
                               ObjectMapper objectMapper, UlidGenerator idGenerator) {
        this.agentMapper = agentMapper;
        this.discussionMapper = discussionMapper;
        this.participantMapper = participantMapper;
        this.objectMapper = objectMapper;
        this.idGenerator = idGenerator;
    }

    /** 待写入的席位（id 在生成阶段就已确定，事件与落库必须一致）。 */
    public record NewSeat(String id, int orderIndex, SeatDraft draft, AgentStatus status) {
        public NewSeat(String id, int orderIndex, SeatDraft draft) {
            this(id, orderIndex, draft, AgentStatus.ACTIVE);
        }
    }

    @Transactional
    public void replacePanel(String discussionId, String goal, TopicAnalysis analysis,
                             List<NewSeat> newSeats, List<String> agentIdsToDelete, boolean countRegen) {
        replacePanel(discussionId, goal, analysis, newSeats, agentIdsToDelete, countRegen, null, null);
    }

    /**
     * 落库席位，并把用户自选的那个席位登记为参与者。
     * 选座和认领必须同一个事务：否则席位已存在、参与者却没写进去，刷新后会变成"无主席位"。
     */
    @Transactional
    public void replacePanel(String discussionId, String goal, TopicAnalysis analysis,
                             List<NewSeat> newSeats, List<String> agentIdsToDelete, boolean countRegen,
                             String userSeatId, String userId) {
        for (String agentId : agentIdsToDelete) {
            agentMapper.deleteById(agentId);
        }
        for (NewSeat seat : newSeats) {
            agentMapper.insert(toEntity(discussionId, seat));
        }
        if (userSeatId != null && userId != null) {
            ParticipantEntity existing = participantMapper.findByDiscussionAndUser(discussionId, userId);
            if (existing == null) {
                ParticipantEntity participant = new ParticipantEntity();
                participant.setId(idGenerator.nextWithPrefix(IdPrefixes.PARTICIPANT));
                participant.setDiscussionId(discussionId);
                participant.setUserId(userId);
                participant.setSeatId(userSeatId);
                participant.setRole(ParticipantRole.PLAYER);
                participantMapper.insert(participant);
            } else {
                existing.setSeatId(userSeatId);
                existing.setRole(ParticipantRole.PLAYER);
                existing.setLeftAt(null);
                participantMapper.updateById(existing);
            }
        }
        discussionMapper.markPanelReady(discussionId, DiscussionStatus.PANEL_READY, goal,
                toJson(analysis), Timestamps.now());
        if (countRegen) {
            discussionMapper.increaseRegenCount(discussionId);
        }
    }

    private AgentEntity toEntity(String discussionId, NewSeat seat) {
        SeatDraft draft = seat.draft();
        AgentEntity entity = new AgentEntity();
        entity.setId(seat.id());
        entity.setDiscussionId(discussionId);
        entity.setRoleType(draft.getRoleType());
        entity.setName(draft.getName());
        entity.setTitle(draft.getTitle());
        entity.setStance(draft.getStance());
        entity.setFocus(draft.getFocus());
        entity.setVoice(draft.getVoice());
        entity.setColorToken(draft.getColorToken());
        entity.setOrderIndex(seat.orderIndex());
        entity.setStatus(seat.status() == null ? AgentStatus.ACTIVE : seat.status());
        // 只存摘要：不向前端暴露原始 prompt
        String digest = DigestUtils.md5DigestAsHex(
                (draft.getStance() + "|" + draft.getVoice()).getBytes(StandardCharsets.UTF_8)).substring(0, 16);
        entity.setSystemPromptDigest(digest + ": " + draft.getTitle() + " / " + draft.getStance());
        return entity;
    }

    private String toJson(TopicAnalysis analysis) {
        try {
            return objectMapper.writeValueAsString(analysis);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    public String nextSeatId() {
        return idGenerator.nextWithPrefix(IdPrefixes.SEAT);
    }
}
