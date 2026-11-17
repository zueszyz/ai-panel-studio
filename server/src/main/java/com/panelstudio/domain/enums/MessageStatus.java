package com.panelstudio.domain.enums;

/** 消息状态：delta 不落库，只在结束时写最终态。 */
public enum MessageStatus {
    PENDING,
    STREAMING,
    DONE,
    FAILED,
    PARTIAL
}
