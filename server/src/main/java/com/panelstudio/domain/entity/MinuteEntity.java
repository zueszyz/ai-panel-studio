package com.panelstudio.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * minutes：会议纪要（版本化）。
 *
 * <p>{@code structuredJson} 刻意用 String 承载，不挂 {@code JacksonTypeHandler}：
 * 纪要的结构是"给前端原样透传的 JSON"，服务端只在落库前做一次
 * 「sourceMessageIds 必须属于本场」的校验，不需要反序列化成强类型对象。</p>
 */
@TableName("minutes")
public class MinuteEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String discussionId;
    private Integer version;
    private String contentMd;
    private String structuredJson;
    private String model;
    private Integer tokensUsed;
    private String status;

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

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getContentMd() {
        return contentMd;
    }

    public void setContentMd(String contentMd) {
        this.contentMd = contentMd;
    }

    public String getStructuredJson() {
        return structuredJson;
    }

    public void setStructuredJson(String structuredJson) {
        this.structuredJson = structuredJson;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Integer getTokensUsed() {
        return tokensUsed;
    }

    public void setTokensUsed(Integer tokensUsed) {
        this.tokensUsed = tokensUsed;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
