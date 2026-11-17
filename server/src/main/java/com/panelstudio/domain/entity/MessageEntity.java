package com.panelstudio.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.panelstudio.domain.enums.MessageKind;
import com.panelstudio.domain.enums.MessageRole;
import com.panelstudio.domain.enums.MessageStatus;

/** messages：讨论的唯一事实流（delta 不落库，结束时写最终态）。 */
@TableName("messages")
public class MessageEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String discussionId;
    private String agentId;
    private String userId;
    private MessageRole role;
    private MessageKind kind;
    private MessageStatus status;
    private Integer roundNo;
    private Integer seq;
    private String content;
    private String quoteMessageId;
    private Integer isInterrupt;
    private Integer tokensIn;
    private Integer tokensOut;
    private Integer latencyMs;
    private Integer attempt;
    private String clientMsgId;

    @TableField(fill = FieldFill.INSERT)
    private String createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updatedAt;

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

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public MessageRole getRole() {
        return role;
    }

    public void setRole(MessageRole role) {
        this.role = role;
    }

    public MessageKind getKind() {
        return kind;
    }

    public void setKind(MessageKind kind) {
        this.kind = kind;
    }

    public MessageStatus getStatus() {
        return status;
    }

    public void setStatus(MessageStatus status) {
        this.status = status;
    }

    public Integer getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(Integer roundNo) {
        this.roundNo = roundNo;
    }

    public Integer getSeq() {
        return seq;
    }

    public void setSeq(Integer seq) {
        this.seq = seq;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getQuoteMessageId() {
        return quoteMessageId;
    }

    public void setQuoteMessageId(String quoteMessageId) {
        this.quoteMessageId = quoteMessageId;
    }

    public Integer getIsInterrupt() {
        return isInterrupt;
    }

    public void setIsInterrupt(Integer isInterrupt) {
        this.isInterrupt = isInterrupt;
    }

    public Integer getTokensIn() {
        return tokensIn;
    }

    public void setTokensIn(Integer tokensIn) {
        this.tokensIn = tokensIn;
    }

    public Integer getTokensOut() {
        return tokensOut;
    }

    public void setTokensOut(Integer tokensOut) {
        this.tokensOut = tokensOut;
    }

    public Integer getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Integer latencyMs) {
        this.latencyMs = latencyMs;
    }

    public Integer getAttempt() {
        return attempt;
    }

    public void setAttempt(Integer attempt) {
        this.attempt = attempt;
    }

    public String getClientMsgId() {
        return clientMsgId;
    }

    public void setClientMsgId(String clientMsgId) {
        this.clientMsgId = clientMsgId;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
