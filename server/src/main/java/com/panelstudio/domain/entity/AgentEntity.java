package com.panelstudio.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.panelstudio.domain.enums.AgentRoleType;
import com.panelstudio.domain.enums.AgentStatus;

import java.util.ArrayList;
import java.util.List;

/** agents：一个席位（含主持人）。 */
@TableName(value = "agents", autoResultMap = true)
public class AgentEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String discussionId;
    private AgentRoleType roleType;
    private String name;
    private String title;
    private String stance;

    @TableField(value = "focus", typeHandler = JacksonTypeHandler.class)
    private List<String> focus = new ArrayList<>();

    private String voice;
    private String colorToken;
    private String systemPromptDigest;
    private Integer orderIndex;
    private AgentStatus status;

    @TableField(fill = FieldFill.INSERT)
    private String createdAt;

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

    public AgentRoleType getRoleType() {
        return roleType;
    }

    public void setRoleType(AgentRoleType roleType) {
        this.roleType = roleType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getStance() {
        return stance;
    }

    public void setStance(String stance) {
        this.stance = stance;
    }

    public List<String> getFocus() {
        return focus;
    }

    public void setFocus(List<String> focus) {
        this.focus = focus;
    }

    public String getVoice() {
        return voice;
    }

    public void setVoice(String voice) {
        this.voice = voice;
    }

    public String getColorToken() {
        return colorToken;
    }

    public void setColorToken(String colorToken) {
        this.colorToken = colorToken;
    }

    public String getSystemPromptDigest() {
        return systemPromptDigest;
    }

    public void setSystemPromptDigest(String systemPromptDigest) {
        this.systemPromptDigest = systemPromptDigest;
    }

    public Integer getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(Integer orderIndex) {
        this.orderIndex = orderIndex;
    }

    public AgentStatus getStatus() {
        return status;
    }

    public void setStatus(AgentStatus status) {
        this.status = status;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
