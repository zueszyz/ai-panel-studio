package com.panelstudio.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.panelstudio.domain.enums.DiscussionStatus;
import com.panelstudio.domain.model.TopicAnalysis;

/** discussions：一场圆桌。 */
@TableName(value = "discussions", autoResultMap = true)
public class DiscussionEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String topic;
    private String goal;
    private DiscussionStatus status;
    private Integer panelSize;
    private Integer turnIndex;

    @TableField(value = "topic_analysis", typeHandler = JacksonTypeHandler.class)
    private TopicAnalysis topicAnalysis;

    private String createdBy;
    private String startedAt;
    private String endedAt;
    private Integer regenCount;

    @TableField(fill = FieldFill.INSERT)
    private String createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updatedAt;

    @TableLogic(value = "null", delval = "now()")
    private String deletedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getGoal() {
        return goal;
    }

    public void setGoal(String goal) {
        this.goal = goal;
    }

    public DiscussionStatus getStatus() {
        return status;
    }

    public void setStatus(DiscussionStatus status) {
        this.status = status;
    }

    public Integer getPanelSize() {
        return panelSize;
    }

    public void setPanelSize(Integer panelSize) {
        this.panelSize = panelSize;
    }

    public Integer getTurnIndex() {
        return turnIndex;
    }

    public void setTurnIndex(Integer turnIndex) {
        this.turnIndex = turnIndex;
    }

    public TopicAnalysis getTopicAnalysis() {
        return topicAnalysis;
    }

    public void setTopicAnalysis(TopicAnalysis topicAnalysis) {
        this.topicAnalysis = topicAnalysis;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
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

    public Integer getRegenCount() {
        return regenCount;
    }

    public void setRegenCount(Integer regenCount) {
        this.regenCount = regenCount;
    }

    public String getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(String deletedAt) {
        this.deletedAt = deletedAt;
    }
}
