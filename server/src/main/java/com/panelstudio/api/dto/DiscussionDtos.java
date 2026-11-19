package com.panelstudio.api.dto;

import com.panelstudio.domain.enums.AgentRoleType;
import com.panelstudio.domain.enums.AgentStatus;
import com.panelstudio.domain.enums.DiscussionStatus;
import com.panelstudio.domain.model.TopicAnalysis;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Locale;

/** 大厅与组局相关的请求/响应体，字段与 docs/API.md §2 对齐。 */
public final class DiscussionDtos {

    private DiscussionDtos() {
    }

    public record CreateDiscussionRequest(
            @NotBlank(message = "议题不能为空")
            @Size(min = 8, max = 200, message = "议题长度需在 8 到 200 字之间")
            String topic,
            @Min(value = 3, message = "参会人数为 3 到 6 人")
            @Max(value = 6, message = "参会人数为 3 到 6 人")
            Integer panelSize) {
    }

    public record CreateDiscussionResponse(
            String id,
            String topic,
            DiscussionStatus status,
            int panelSize,
            int turnIndex,
            String createdAt) {
    }

    public record SeatBrief(
            String id,
            String name,
            String title,
            String colorToken,
            AgentStatus status,
            boolean claimedByMe) {
    }

    public record LastMessageView(String preview, String speaker, String at) {
    }

    public record DeleteDiscussionResponse(String id, boolean deleted) {
    }

    public record DiscussionSummary(
            String id,
            String topic,
            boolean createdByMe,
            DiscussionStatus status,
            int panelSize,
            int turnIndex,
            List<SeatBrief> seats,
            int openSeatCount,
            LastMessageView lastMessage,
            String updatedAt) {
    }

    public record DiscussionListResponse(List<DiscussionSummary> items, String nextCursor) {
    }

    public record SeatView(
            String id,
            AgentRoleType roleType,
            String name,
            String title,
            String stance,
            List<String> focus,
            String voice,
            String colorToken,
            int orderIndex,
            AgentStatus status,
            boolean claimedByMe) {
    }

    public record DiscussionDetail(
            String id,
            String topic,
            boolean createdByMe,
            String goal,
            DiscussionStatus status,
            int panelSize,
            int turnIndex,
            List<SeatView> seats,
            TopicAnalysis topicAnalysis,
            String mySeatId,
            String createdAt,
            String updatedAt) {
    }

    public record PanelQueryResponse(String discussionId, DiscussionStatus status, List<SeatView> seats) {
    }

    public record ClaimRequest(String seatId) {
    }

    public record ClaimResponse(String seatId, String role, String displayName, AgentStatus seatStatus) {
    }

    /**
     * 组局请求。{@code userRole} 是用户自选的专家角色（如 STRATEGIST），
     * 服务端据此保留该席位，只让 AI 生成其余席位；{@code autoStart} 表示生成完立即开场。
     */
    public record CreatePanelRequest(Boolean regenerate, Boolean preset, String userRole, Boolean autoStart) {
        public boolean regenerateRequested() {
            return Boolean.TRUE.equals(regenerate);
        }

        public boolean presetRequested() {
            return Boolean.TRUE.equals(preset);
        }

        public boolean autoStartRequested() {
            return Boolean.TRUE.equals(autoStart);
        }

        public String userRoleValue() {
            return userRole == null || userRole.isBlank() ? null : userRole.trim().toUpperCase(Locale.ROOT);
        }
    }
}
