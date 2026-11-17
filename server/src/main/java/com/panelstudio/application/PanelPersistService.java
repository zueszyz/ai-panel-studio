package com.panelstudio.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.panelstudio.domain.entity.AgentEntity;
import com.panelstudio.domain.enums.AgentStatus;
import com.panelstudio.domain.enums.DiscussionStatus;
import com.panelstudio.domain.model.SeatDraft;
import com.panelstudio.domain.model.TopicAnalysis;
import com.panelstudio.domain.support.IdPrefixes;
import com.panelstudio.domain.support.Timestamps;
import com.panelstudio.domain.support.UlidGenerator;
import com.panelstudio.infra.mapper.AgentMapper;
import com.panelstudio.infra.mapper.DiscussionMapper;
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
    private final ObjectMapper objectMapper;
    private final UlidGenerator idGenerator;

    public PanelPersistService(AgentMapper agentMapper, DiscussionMapper discussionMapper,
                               ObjectMapper objectMapper, UlidGenerator idGenerator) {
        this.agentMapper = agentMapper;
        this.discussionMapper = discussionMapper;
        this.objectMapper = objectMapper;
        this.idGenerator = idGenerator;
    }

    /** 待写入的席位（id 在生成阶段就已确定，事件与落库必须一致）。 */
    public record NewSeat(String id, int orderIndex, SeatDraft draft) {
    }

    @Transactional
    public void replacePanel(String discussionId, String goal, TopicAnalysis analysis,
                             List<NewSeat> newSeats, List<String> agentIdsToDelete, boolean countRegen) {
        for (String agentId : agentIdsToDelete) {
            agentMapper.deleteById(agentId);
        }
        for (NewSeat seat : newSeats) {
            agentMapper.insert(toEntity(discussionId, seat));
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
        entity.setStatus(AgentStatus.ACTIVE);
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
