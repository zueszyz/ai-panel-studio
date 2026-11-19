package com.panelstudio.application.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** I-10：单场预算 80% 提示收敛、100% 拒绝新发言但不影响结束出纪要。 */
class TokenBudgetTest {

    @Test
    @DisplayName("未用满可发言；到 80% 进入收敛提示；到 100% 不能再发言")
    void shouldEnforceBudgetThresholds() {
        TokenBudget budget = new TokenBudget(100, 0.8);
        assertThat(budget.canSpeak()).isTrue();
        assertThat(budget.shouldWrapUp()).isFalse();

        budget.consume(80);
        assertThat(budget.shouldWrapUp()).isTrue();
        assertThat(budget.canSpeak()).isTrue();
        assertThat(budget.usagePercent()).isEqualTo(80);

        budget.consume(20);
        assertThat(budget.canSpeak()).isFalse();
        assertThat(budget.usagePercent()).isEqualTo(100);
    }

    @Test
    @DisplayName("负数与零消耗被忽略，百分比封顶 100")
    void shouldIgnoreNonPositiveConsumption() {
        TokenBudget budget = new TokenBudget(10, 0.8);
        budget.consume(-5);
        budget.consume(0);
        assertThat(budget.used()).isZero();

        budget.consume(50);
        assertThat(budget.usagePercent()).isEqualTo(100);
    }
}
