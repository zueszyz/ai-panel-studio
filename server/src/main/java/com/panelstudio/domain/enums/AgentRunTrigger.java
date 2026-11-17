package com.panelstudio.domain.enums;

/** agent_runs.trigger_type：这条发言"为什么发生"，轨迹面板据此显示触发来源。 */
public enum AgentRunTrigger {
    ROUND_START,
    HOST_ASSIGN,
    USER_INTERRUPT,
    PEER_QUOTE,
    DIGEST
}
