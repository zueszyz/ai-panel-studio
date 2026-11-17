package com.panelstudio.domain.enums;

/** 席位状态：在场 / 被用户接手 / 已离席（换一批时被替换）。 */
public enum AgentStatus {
    ACTIVE,
    REPLACED_BY_USER,
    RETIRED
}
