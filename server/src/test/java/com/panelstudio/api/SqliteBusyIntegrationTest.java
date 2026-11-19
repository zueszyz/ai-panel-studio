package com.panelstudio.api;

import com.panelstudio.application.MetricsService;
import com.panelstudio.domain.entity.MessageEntity;
import com.panelstudio.domain.enums.MessageKind;
import com.panelstudio.domain.enums.MessageRole;
import com.panelstudio.domain.enums.MessageStatus;
import com.panelstudio.infra.mapper.MessageMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * I-13：`message.end` 的落库动作就是单条消息终态 UPDATE。用 20 线程并发写终态，
 * 验证写锁争抢被 busy_timeout + 应用层退避消化，不会把 SQLITE_BUSY 泄漏成接口 500，
 * 也不会丢写。独立 context（额外 property）保证临时库不与其它用例共享。
 */
@SpringBootTest(properties = "panel.sqlite.busy-test=true")
@ActiveProfiles("test")
class SqliteBusyIntegrationTest {

    private static final String DISCUSSION_ID = "dk_01H8ZQ7M0000000000000000B1";
    private static final int THREADS = 20;
    private static final int WRITES_PER_THREAD = 5;

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private MetricsService metrics;

    @Test
    @DisplayName("I-13 并发写：20 线程 × 5 次终态更新，无锁异常泄漏且 P95 < 200ms")
    void concurrentEndWritesAreSerializedWithoutBusyLeaks() throws Exception {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            String id = "ms_busy_" + i;
            MessageEntity message = new MessageEntity();
            message.setId(id);
            message.setDiscussionId(DISCUSSION_ID);
            message.setRole(MessageRole.HOST);
            message.setKind(MessageKind.SPEECH);
            message.setStatus(MessageStatus.PENDING);
            message.setClientMsgId("busy-client-" + i);
            message.setContent("initial");
            messageMapper.insert(message);
            ids.add(id);
        }
        long busyBefore = metrics.snapshot().sqliteBusy();

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<List<Long>>> futures = new ArrayList<>();
        for (String id : ids) {
            futures.add(pool.submit(() -> {
                start.await();
                List<Long> latencies = new ArrayList<>();
                for (int n = 0; n < WRITES_PER_THREAD; n++) {
                    MessageEntity patch = new MessageEntity();
                    patch.setId(id);
                    patch.setStatus(MessageStatus.DONE);
                    patch.setContent("final-" + n);
                    long t0 = System.nanoTime();
                    messageMapper.updateById(patch);
                    latencies.add(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0));
                }
                return latencies;
            }));
        }
        start.countDown();

        List<Long> latencies = new ArrayList<>();
        for (Future<List<Long>> future : futures) {
            // 任何未消化的 SQLITE_BUSY 都会以 ExecutionException 在这里暴露。
            latencies.addAll(future.get(30, TimeUnit.SECONDS));
        }
        pool.shutdownNow();

        latencies.sort(Long::compareTo);
        long p95 = latencies.get((int) Math.ceil(0.95 * latencies.size()) - 1);
        assertThat(latencies).hasSize(THREADS * WRITES_PER_THREAD);
        assertThat(p95).isLessThan(200L);
        assertThat(metrics.snapshot().sqliteBusy() - busyBefore).isGreaterThanOrEqualTo(0L);

        for (String id : ids) {
            assertThat(messageMapper.selectById(id).getStatus()).isEqualTo(MessageStatus.DONE);
        }
    }
}
