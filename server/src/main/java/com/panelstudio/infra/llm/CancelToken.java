package com.panelstudio.infra.llm;

/**
 * 单条生成的取消凭据：讨论结束时调度器调用 {@link #cancel()}，
 * 由 LlmClient 内部把 Reactor 订阅 dispose 掉，避免白烧 token。
 */
public final class CancelToken {

    private final java.util.concurrent.atomic.AtomicBoolean cancelled =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private volatile Runnable onCancel;

    public boolean isCancelled() {
        return cancelled.get();
    }

    public void onCancel(Runnable action) {
        this.onCancel = action;
        if (cancelled.get() && action != null) {
            action.run();
        }
    }

    public void cancel() {
        if (cancelled.compareAndSet(false, true)) {
            Runnable action = onCancel;
            if (action != null) {
                action.run();
            }
        }
    }
}
