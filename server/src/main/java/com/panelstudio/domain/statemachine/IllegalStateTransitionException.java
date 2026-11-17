package com.panelstudio.domain.statemachine;

import com.panelstudio.domain.enums.DiscussionStatus;

/** 非法状态迁移（属于编程错误或并发冲突，映射为 409）。 */
public class IllegalStateTransitionException extends RuntimeException {

    private final DiscussionStatus from;
    private final DiscussionStatus to;

    public IllegalStateTransitionException(DiscussionStatus from, DiscussionStatus to) {
        super("不允许的状态迁移: " + from + " -> " + to);
        this.from = from;
        this.to = to;
    }

    public DiscussionStatus from() {
        return from;
    }

    public DiscussionStatus to() {
        return to;
    }
}
