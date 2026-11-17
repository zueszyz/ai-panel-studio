package com.panelstudio.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.panelstudio.domain.enums.AgentRunPhase;
import com.panelstudio.domain.enums.AgentRunTrigger;

import java.util.LinkedHashMap;
import java.util.Map;

/** agent_runs：每次模型调用一行，轨迹回放与失败率统计的数据源（不存 prompt 原文）。 */
@TableName(value = "agent_runs", autoResultMap = true)
public class AgentRunEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String discussionId;
    private String agentId;
    private String messageId;
    private AgentRunPhase phase;
    private AgentRunTrigger triggerType;
    private Integer roundNo;
    private String status;
    private String errorCode;
    private Integer durationMs;
    private Integer tokens;

    @TableField(value = "meta", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> meta = new LinkedHashMap<>();

    private String startedAt;
    private String endedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDiscussionId() {
        return discussionId;
    }

    public void setDiscussionId(String discussionId) {
        this.discussionId = discussionId;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public AgentRunPhase getPhase() {
        return phase;
    }

    public void setPhase(AgentRunPhase phase) {
        this.phase = phase;
    }

    public AgentRunTrigger getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(AgentRunTrigger triggerType) {
        this.triggerType = triggerType;
    }

    public Integer getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(Integer roundNo) {
        this.roundNo = roundNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public Integer getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Integer durationMs) {
        this.durationMs = durationMs;
    }

    public Integer getTokens() {
        return tokens;
    }

    public void setTokens(Integer tokens) {
        this.tokens = tokens;
    }

    public Map<String, Object> getMeta() {
        return meta;
    }

    public void setMeta(Map<String, Object> meta) {
        this.meta = meta;
    }

    public String getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(String startedAt) {
        this.startedAt = startedAt;
    }

    public String getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(String endedAt) {
        this.endedAt = endedAt;
    }
}
