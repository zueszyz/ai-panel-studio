package com.panelstudio.api.dto;

import com.panelstudio.domain.enums.DiscussionStatus;

import java.util.List;

/** 组局流的 4 类事件载荷（docs/API.md §2.3）。 */
public final class PanelEvents {

    private PanelEvents() {
    }

    public record Analysis(String goal, List<String> disputes, List<String> angles, List<String> audience) {
    }

    public record Done(String discussionId, DiscussionStatus status, int seats, String fallback) {
    }

    public record Failed(String code, String message, boolean retriable, boolean fallbackAvailable) {
    }
}
