package com.panelstudio.domain.enums;

/**
 * 圆桌状态。落库为 TEXT，与 V1__schema.sql 的 CHECK 约束取值一致。
 */
public enum DiscussionStatus {
    DRAFT,
    PANEL_READY,
    LIVE,
    SUMMARIZING,
    CLOSED,
    INTERRUPTED,
    FAILED;

    /** 大厅「进行中」分组：席位已就绪或正在讨论。 */
    public boolean isOngoing() {
        return this == PANEL_READY || this == LIVE || this == SUMMARIZING || this == INTERRUPTED;
    }
}
