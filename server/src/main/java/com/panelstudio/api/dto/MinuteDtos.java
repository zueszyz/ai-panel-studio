package com.panelstudio.api.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/** 纪要相关的请求/响应与 SSE 载荷（docs/API.md §2.9–§2.10）。 */
public final class MinuteDtos {

    private MinuteDtos() {
    }

    /** 分节到达：前端先把已到达的段落渲染出来，不等全文。 */
    public record SectionEvent(String key, int index, String md) {
    }

    public record DoneEvent(String id, int version, int tokensUsed, String fallback) {
    }

    /** 失败不覆盖旧版本：keptVersion 告诉前端仍可查看哪一版。 */
    public record FailedEvent(String code, boolean retriable, Integer keptVersion) {
    }

    /** GET 响应；versions 供版本切换，是契约里的附加字段。 */
    public record MinuteView(String id,
                             String discussionId,
                             int version,
                             String status,
                             String contentMd,
                             JsonNode structured,
                             String model,
                             int tokensUsed,
                             String createdAt,
                             List<Integer> versions) {
    }
}
