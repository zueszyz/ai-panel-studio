package com.panelstudio.application.session;

import com.panelstudio.infra.config.AppProperties;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 单场 token 预算：80% 提示收敛，100% 拒绝继续发言但允许结束出纪要（rules/30 §3）。
 * 每个 SessionActor 一个实例，互不借额。
 */
public final class TokenBudget {

    private final long limit;
    private final double warnRatio;
    private final AtomicLong used = new AtomicLong();

    public TokenBudget(long limit, double warnRatio) {
        this.limit = limit;
        this.warnRatio = warnRatio;
    }

    public static TokenBudget from(AppProperties properties) {
        return new TokenBudget(properties.getBudget().getDiscussionTokens(),
                properties.getBudget().getWarnRatio());
    }

    public void consume(long tokens) {
        if (tokens > 0) {
            used.addAndGet(tokens);
        }
    }

    public long used() {
        return used.get();
    }

    public long limit() {
        return limit;
    }

    /** 是否还能继续发言（100% 之后禁止新发言）。 */
    public boolean canSpeak() {
        return used.get() < limit;
    }

    /** 是否进入收敛提示区间（≥80%）。 */
    public boolean shouldWrapUp() {
        return used.get() >= (long) (limit * warnRatio);
    }

    public int usagePercent() {
        return (int) Math.min(100, Math.round(used.get() * 100.0 / limit));
    }
}
