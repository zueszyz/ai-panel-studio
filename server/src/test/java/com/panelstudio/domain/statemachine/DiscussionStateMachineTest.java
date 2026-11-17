package com.panelstudio.domain.statemachine;

import com.panelstudio.domain.enums.DiscussionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiscussionStateMachineTest {

    @Test
    @DisplayName("主链路 DRAFT → PANEL_READY → LIVE → SUMMARIZING → CLOSED 合法")
    void shouldAllowHappyPath() {
        assertThat(DiscussionStateMachine.canTransit(DiscussionStatus.DRAFT, DiscussionStatus.PANEL_READY)).isTrue();
        assertThat(DiscussionStateMachine.canTransit(DiscussionStatus.PANEL_READY, DiscussionStatus.LIVE)).isTrue();
        assertThat(DiscussionStateMachine.canTransit(DiscussionStatus.LIVE, DiscussionStatus.SUMMARIZING)).isTrue();
        assertThat(DiscussionStateMachine.canTransit(DiscussionStatus.SUMMARIZING, DiscussionStatus.CLOSED)).isTrue();
    }

    @Test
    @DisplayName("CLOSED → LIVE 非法，抛 IllegalStateTransitionException")
    void shouldRejectClosedToLive() {
        assertThatThrownBy(() -> DiscussionStateMachine.transit(DiscussionStatus.CLOSED, DiscussionStatus.LIVE))
                .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    @DisplayName("INTERRUPTED 可双向：回 LIVE 或直接 SUMMARIZING")
    void shouldAllowInterruptedBothWays() {
        assertThat(DiscussionStateMachine.canTransit(DiscussionStatus.LIVE, DiscussionStatus.INTERRUPTED)).isTrue();
        assertThat(DiscussionStateMachine.canTransit(DiscussionStatus.INTERRUPTED, DiscussionStatus.LIVE)).isTrue();
        assertThat(DiscussionStateMachine.canTransit(DiscussionStatus.INTERRUPTED, DiscussionStatus.SUMMARIZING)).isTrue();
    }

    @Test
    @DisplayName("FAILED 只能回到 DRAFT 重试")
    void shouldOnlyAllowFailedBackToDraft() {
        assertThat(DiscussionStateMachine.canTransit(DiscussionStatus.FAILED, DiscussionStatus.DRAFT)).isTrue();
        assertThat(DiscussionStateMachine.canTransit(DiscussionStatus.FAILED, DiscussionStatus.LIVE)).isFalse();
    }
}
