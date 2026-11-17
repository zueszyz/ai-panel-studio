package com.panelstudio.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.panelstudio.domain.enums.ParticipantRole;

/** discussion_participants：认领（PLAYER 带 seatId）或旁听（VIEWER）。 */
@TableName("discussion_participants")
public class ParticipantEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String discussionId;
    private String userId;
    private String seatId;
    private ParticipantRole role;

    @TableField(fill = FieldFill.INSERT)
    private String joinedAt;

    private String leftAt;

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

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getSeatId() {
        return seatId;
    }

    public void setSeatId(String seatId) {
        this.seatId = seatId;
    }

    public ParticipantRole getRole() {
        return role;
    }

    public void setRole(ParticipantRole role) {
        this.role = role;
    }

    public String getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(String joinedAt) {
        this.joinedAt = joinedAt;
    }

    public String getLeftAt() {
        return leftAt;
    }

    public void setLeftAt(String leftAt) {
        this.leftAt = leftAt;
    }
}
