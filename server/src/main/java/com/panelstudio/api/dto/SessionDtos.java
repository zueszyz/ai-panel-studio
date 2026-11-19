package com.panelstudio.api.dto;

import com.panelstudio.api.dto.StreamEvents.RunView;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 讨论内核的请求/响应体（docs/API.md §2.5–§2.8、§2.11）。 */
public final class SessionDtos {

    private SessionDtos() {
    }

    public record StartResponse(String status, int turnIndex, String openingMessageId) {
    }

    public record PostMessageRequest(@NotBlank(message = "说点什么")
                                     @Size(min = 1, max = 1000, message = "单条发言 1–1000 字")
                                     String content,
                                     @NotBlank(message = "缺少幂等键")
                                     String clientMsgId,
                                     String quoteMessageId) {
    }

    public record PostMessageResponse(String id, String status, boolean enqueued, int expectedResponders) {
    }

    public record TurnResponse(int turnIndex, List<String> speakers) {
    }

    /** 单条重试：沿用同一 messageId，前端卡片原位回到流式态。 */
    public record RetryMessageResponse(String id, String status, int attempt) {
    }

    public record EndResponse(String status, String endedAt) {
    }

    public record RunsResponse(List<RunView> items) {
    }
}
