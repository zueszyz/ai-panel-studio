package com.panelstudio.api.dto;

import com.panelstudio.api.dto.DiscussionDtos.SeatView;

import java.util.List;
import java.util.Map;

/** `GET /discussions/:id/stream` 的事件载荷（与 docs/API.md §3.2 一一对应）。 */
public final class StreamEvents {

    private StreamEvents() {
    }

    public record Snapshot(String discussionId,
                           String topic,
                           String goal,
                           String status,
                           int turnIndex,
                           String mySeatId,
                           List<SeatView> seats,
                           List<MessageView> messages,
                           List<RunView> runs) {
    }

    public record DiscussionUpdated(String status, Integer turnIndex, Integer budgetPercent) {
    }

    public record TurnStart(int turnIndex, List<String> speakers, String reason) {
    }

    public record AgentStatus(String seatId, String status) {
    }

    public record MessageStart(String messageId, String seatId, String role, String kind,
                               int roundNo, String quoteMessageId) {
    }

    public record MessageDelta(String messageId, int seq, String text) {
    }

    public record MessageEnd(String messageId, String content, int tokens, long latencyMs, String status) {
    }

    public record MessageFailed(String messageId, String code, boolean retriable) {
    }

    public record DigestStart(String messageId) {
    }

    public record DiscussionClosed(String status, String endedAt) {
    }

    public record ErrorFrame(String code, String message, boolean retriable) {
    }

    /** 一条消息的完整视图（快照与历史加载复用）。 */
    public record MessageView(String id,
                              String seatId,
                              String seatName,
                              String seatColorToken,
                              String role,
                              String kind,
                              String status,
                              int roundNo,
                              String content,
                              String quoteMessageId,
                              String quotePreview,
                              int isInterrupt,
                              int tokensOut,
                              Integer latencyMs,
                              int attempt,
                              String createdAt) {
    }

    /** 轨迹条目：默认只给业务字段，技术细节在 meta 里（前端默认折叠）。 */
    public record RunView(String id,
                          String agentId,
                          String messageId,
                          String phase,
                          String trigger,
                          int roundNo,
                          Integer durationMs,
                          int tokens,
                          Map<String, Object> meta,
                          String startedAt,
                          String endedAt) {
    }
}
