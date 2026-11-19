package com.panelstudio.application;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * 轻量业务指标（不引 Prometheus）：供 {@code GET /api/metrics} 读取。
 * 数据由 orchestrator / SSE 层写入，M1 阶段所有计数为 0。
 */
@Service
public class MetricsService {

    private static final int MAX_SAMPLES = 500;

    private final AtomicInteger sseConnections = new AtomicInteger();
    private final AtomicInteger activeSessions = new AtomicInteger();
    private final Queue<Long> firstTokenSamples = new ConcurrentLinkedQueue<>();
    private final LongAdder agentCalls = new LongAdder();
    private final LongAdder agentFailures = new LongAdder();
    /** SQLite 写锁冲突后由应用层退避重试消化的次数（ARCHITECTURE §3.8）。 */
    private final LongAdder sqliteBusy = new LongAdder();

    public void sseConnected() {
        sseConnections.incrementAndGet();
    }

    public void sseDisconnected() {
        sseConnections.updateAndGet(current -> Math.max(0, current - 1));
    }

    public void sessionStarted() {
        activeSessions.incrementAndGet();
    }

    public void sessionEnded() {
        activeSessions.updateAndGet(current -> Math.max(0, current - 1));
    }

    public void recordFirstToken(long millis) {
        if (millis < 0) {
            return;
        }
        firstTokenSamples.add(millis);
        while (firstTokenSamples.size() > MAX_SAMPLES) {
            firstTokenSamples.poll();
        }
    }

    public void agentCall() {
        agentCalls.increment();
    }

    public void agentFailure() {
        agentCalls.increment();
        agentFailures.increment();
    }

    public void sqliteBusy() {
        sqliteBusy.increment();
    }

    public Snapshot snapshot() {
        List<Long> samples = new ArrayList<>(firstTokenSamples);
        samples.sort(Long::compareTo);
        return new Snapshot(
                sseConnections.get(),
                activeSessions.get(),
                new Latency(percentile(samples, 0.50), percentile(samples, 0.95)),
                agentCalls.sum() == 0 ? 0d : round(agentFailures.doubleValue() / agentCalls.sum()),
                sqliteBusy.sum());
    }

    private static long percentile(List<Long> sorted, double p) {
        if (sorted.isEmpty()) {
            return 0L;
        }
        int index = (int) Math.ceil(p * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    private static double round(double value) {
        return Math.round(value * 1000d) / 1000d;
    }

    public record Latency(long p50, long p95) {
    }

    public record Snapshot(int sseConnections, int activeSessions, Latency firstTokenMs,
                           double agentFailureRate, long sqliteBusy) {
    }
}
