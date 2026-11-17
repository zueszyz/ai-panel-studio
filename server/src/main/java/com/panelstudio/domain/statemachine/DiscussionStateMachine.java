package com.panelstudio.domain.statemachine;

import com.panelstudio.domain.enums.DiscussionStatus;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 圆桌状态机：唯一合法迁移集合。所有状态变更必须经过这里，
 * 禁止在 Service 里散落 {@code if (status == ...)} 直接改状态。
 */
public final class DiscussionStateMachine {

    private static final Map<DiscussionStatus, Set<DiscussionStatus>> ALLOWED;

    static {
        Map<DiscussionStatus, Set<DiscussionStatus>> map = new EnumMap<>(DiscussionStatus.class);
        map.put(DiscussionStatus.DRAFT, EnumSet.of(DiscussionStatus.PANEL_READY, DiscussionStatus.FAILED));
        map.put(DiscussionStatus.PANEL_READY, EnumSet.of(DiscussionStatus.LIVE, DiscussionStatus.FAILED));
        map.put(DiscussionStatus.LIVE, EnumSet.of(DiscussionStatus.SUMMARIZING, DiscussionStatus.INTERRUPTED));
        map.put(DiscussionStatus.INTERRUPTED,
                EnumSet.of(DiscussionStatus.LIVE, DiscussionStatus.SUMMARIZING));
        map.put(DiscussionStatus.SUMMARIZING,
                EnumSet.of(DiscussionStatus.CLOSED, DiscussionStatus.LIVE, DiscussionStatus.FAILED));
        map.put(DiscussionStatus.FAILED, EnumSet.of(DiscussionStatus.DRAFT));
        map.put(DiscussionStatus.CLOSED, EnumSet.noneOf(DiscussionStatus.class));
        ALLOWED = Collections.unmodifiableMap(map);
    }

    private DiscussionStateMachine() {
    }

    public static boolean canTransit(DiscussionStatus from, DiscussionStatus to) {
        if (from == null || to == null) {
            return false;
        }
        if (from == to) {
            return true;
        }
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static DiscussionStatus transit(DiscussionStatus from, DiscussionStatus to) {
        if (!canTransit(from, to)) {
            throw new IllegalStateTransitionException(from, to);
        }
        return to;
    }
}
