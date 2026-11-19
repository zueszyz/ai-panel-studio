package com.panelstudio.application.session;

import com.panelstudio.api.dto.StreamEvents.MessageDelta;
import com.panelstudio.api.dto.StreamEvents.MessageEnd;
import com.panelstudio.api.dto.StreamEvents.MessageFailed;
import com.panelstudio.domain.entity.AgentEntity;
import com.panelstudio.domain.entity.MessageEntity;
import com.panelstudio.domain.enums.AgentRoleType;
import com.panelstudio.domain.enums.AgentRunTrigger;
import com.panelstudio.domain.enums.MessageKind;
import com.panelstudio.domain.enums.MessageStatus;
import com.panelstudio.domain.model.ChatMessage;
import com.panelstudio.domain.support.UlidGenerator;
import com.panelstudio.infra.config.AppProperties;
import com.panelstudio.infra.llm.CancelToken;
import com.panelstudio.infra.llm.LlmClient;
import com.panelstudio.infra.llm.LlmException;
import com.panelstudio.infra.llm.LlmResult;
import com.panelstudio.infra.llm.StreamListener;
import com.panelstudio.infra.mapper.AgentRunMapper;
import com.panelstudio.infra.mapper.MessageMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentRunnerTest {

    private final AppProperties properties = new AppProperties();
    private final CollectingPublisher publisher = new CollectingPublisher();
    private final MessageMapper messageMapper = mock(MessageMapper.class);
    private final AgentRunMapper runMapper = mock(AgentRunMapper.class);
    private final FakeLlmClient llm = new FakeLlmClient();
    private AgentRunner runner;
    private ExecutorService pool;

    @BeforeEach
    void setUp() {
        properties.getLlm().setModel("test-model");
        runner = new AgentRunner(llm, new ContextBuilder(), messageMapper, runMapper, publisher,
                properties, new UlidGenerator(), new com.panelstudio.application.MetricsService());
        pool = Executors.newFixedThreadPool(3);
    }

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
    }

    private AgentEntity seat(String id, String name, AgentRoleType role) {
        AgentEntity seat = new AgentEntity();
        seat.setId(id);
        seat.setName(name);
        seat.setTitle("顾问");
        seat.setStance("有立场");
        seat.setVoice("短句");
        seat.setFocus(List.of("成本"));
        seat.setRoleType(role);
        seat.setDiscussionId("dk_1");
        return seat;
    }

    private AgentRunner.RunRequest request(AgentEntity seat) {
        return new AgentRunner.RunRequest("dk_1", "要不要换向量召回", "判断该不该迁移", seat, null,
                1, MessageKind.SPEECH, AgentRunTrigger.ROUND_START, null, null, null,
                List.of(), Map.of(seat.getId(), seat.getName()), false, false, false);
    }

    @Test
    @DisplayName("两个席位并行发言：快的不被慢的拖住（首尾时间戳可证）")
    void shouldNotBlockEachOther() throws Exception {
        llm.delayFor("陈立", 500);
        llm.delayFor("周敏", 40);
        AgentEntity slow = seat("st_1", "陈立", AgentRoleType.STRATEGIST);
        AgentEntity fast = seat("st_2", "周敏", AgentRoleType.ANALYST);

        long startedAt = System.currentTimeMillis();
        Future<MessageEntity> slowFuture = pool.submit(() -> runner.run(request(slow), new CancelToken()));
        Future<MessageEntity> fastFuture = pool.submit(() -> runner.run(request(fast), new CancelToken()));
        MessageEntity fastMessage = fastFuture.get(5, TimeUnit.SECONDS);
        long fastDoneAt = System.currentTimeMillis();
        MessageEntity slowMessage = slowFuture.get(5, TimeUnit.SECONDS);

        assertThat(fastDoneAt - startedAt).isLessThan(400);
        assertThat(fastMessage.getStatus()).isEqualTo(MessageStatus.DONE);
        assertThat(slowMessage.getStatus()).isEqualTo(MessageStatus.DONE);
        assertThat(llm.finishedAt("周敏")).isLessThan(llm.finishedAt("陈立"));
    }

    @Test
    @DisplayName("单条超时只标记自己失败，不抛出、不影响并发方")
    void shouldIsolateFailure() throws Exception {
        llm.failFor("林恒");
        AgentEntity broken = seat("st_3", "林恒", AgentRoleType.TECH);
        AgentEntity healthy = seat("st_2", "周敏", AgentRoleType.ANALYST);

        Future<MessageEntity> brokenFuture = pool.submit(() -> runner.run(request(broken), new CancelToken()));
        Future<MessageEntity> healthyFuture = pool.submit(() -> runner.run(request(healthy), new CancelToken()));

        MessageEntity brokenMessage = brokenFuture.get(5, TimeUnit.SECONDS);
        MessageEntity healthyMessage = healthyFuture.get(5, TimeUnit.SECONDS);

        assertThat(brokenMessage.getStatus()).isEqualTo(MessageStatus.FAILED);
        assertThat(healthyMessage.getStatus()).isEqualTo(MessageStatus.DONE);
        assertThat(publisher.countOf("message.failed")).isEqualTo(1);
        assertThat(publisher.failedCodes()).containsExactly("LLM_TIMEOUT");
    }

    @Test
    @DisplayName("模型限流：除失败卡片外再发 error 帧，让顶部横幅可见（FR-9）")
    void shouldSurfaceRateLimitBanner() {
        llm.rateLimitFor("周敏");

        MessageEntity message = runner.run(request(seat("st_2", "周敏", AgentRoleType.ANALYST)),
                new CancelToken());

        assertThat(message.getStatus()).isEqualTo(MessageStatus.FAILED);
        assertThat(publisher.failedCodes()).containsExactly("LLM_RATE_LIMIT");
        assertThat(publisher.countOf("error")).isEqualTo(1);
    }

    @Test
    @DisplayName("终态落库失败：发 DB_WRITE_FAILED 失败帧，不让卡片停在 STREAMING")
    void shouldReportDbWriteFailure() {
        when(messageMapper.updateById(any(MessageEntity.class)))
                .thenThrow(new org.springframework.dao.DataRetrievalFailureException("database is locked"));

        MessageEntity message = runner.run(request(seat("st_2", "周敏", AgentRoleType.ANALYST)),
                new CancelToken());

        assertThat(message.getStatus()).isEqualTo(MessageStatus.FAILED);
        assertThat(publisher.countOf("message.failed")).isEqualTo(1);
        assertThat(publisher.failedCodes()).containsExactly("DB_WRITE_FAILED");
    }

    @Test
    @DisplayName("delta 按窗口合并：事件数远少于分片数，内容完整")
    void shouldCoalesceDeltas() {
        llm.chunks("周敏", 24);
        MessageEntity message = runner.run(request(seat("st_2", "周敏", AgentRoleType.ANALYST)),
                new CancelToken());

        int deltas = publisher.countOf("message.delta");
        assertThat(deltas).isGreaterThan(0).isLessThan(24);
        assertThat(message.getContent()).hasSizeGreaterThan(0);
        String joined = publisher.payloadsOf("message.delta").stream()
                .map(payload -> ((MessageDelta) payload).text())
                .reduce("", String::concat);
        assertThat(joined).isEqualTo(message.getContent());
        assertThat(publisher.countOf("message.end")).isEqualTo(1);
    }

    // ── 测试替身 ────────────────────────────────────────────

    private static final class FakeLlmClient implements LlmClient {
        private final Map<String, Long> delays = new LinkedHashMap<>();
        private final Map<String, Long> finishedAt = new LinkedHashMap<>();
        private final Map<String, Integer> chunkCounts = new LinkedHashMap<>();
        private final List<String> failing = new ArrayList<>();
        private final List<String> rateLimited = new ArrayList<>();

        void rateLimitFor(String name) {
            rateLimited.add(name);
        }

        void delayFor(String name, long millis) {
            delays.put(name, millis);
        }

        void failFor(String name) {
            failing.add(name);
        }

        void chunks(String name, int count) {
            chunkCounts.put(name, count);
        }

        long finishedAt(String name) {
            return finishedAt.getOrDefault(name, Long.MAX_VALUE);
        }

        @Override
        public LlmResult chatJson(String systemPrompt, String userPrompt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LlmResult chatStream(List<ChatMessage> messages, StreamListener listener, CancelToken token) {
            String system = messages.get(0).content();
            String name = system.contains("陈立") ? "陈立"
                    : system.contains("周敏") ? "周敏"
                    : system.contains("林恒") ? "林恒" : "unknown";
            long delay = delays.getOrDefault(name, 20L);
            int chunks = chunkCounts.getOrDefault(name, 3);
            if (failing.contains(name)) {
                listener.onDelta("半句话");
                throw LlmException.timeout();
            }
            if (rateLimited.contains(name)) {
                throw LlmException.rateLimited();
            }
            try {
                Thread.sleep(delay / 4);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            listener.onFirstToken(delay / 4);
            StringBuilder content = new StringBuilder();
            for (int index = 0; index < chunks; index++) {
                if (token.isCancelled()) {
                    throw LlmException.cancelled();
                }
                String piece = name + index + "；";
                content.append(piece);
                listener.onDelta(piece);
                try {
                    Thread.sleep(Math.max(1, delay / 4 / chunks));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
            finishedAt.put(name, System.currentTimeMillis());
            return new LlmResult(content.toString(), 10, 12, 100L);
        }
    }

    private static final class CollectingPublisher implements EventPublisher {
        private record Frame(String event, Object payload) {
        }

        private final List<Frame> frames = Collections.synchronizedList(new ArrayList<>());
        private final List<String> events = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void publish(String discussionId, String event, Object payload) {
            frames.add(new Frame(event, payload));
            events.add(event);
        }

        int countOf(String event) {
            synchronized (events) {
                return (int) events.stream().filter(event::equals).count();
            }
        }

        List<Object> payloadsOf(String event) {
            synchronized (frames) {
                return frames.stream().filter(frame -> frame.event().equals(event))
                        .map(Frame::payload).toList();
            }
        }

        List<String> failedCodes() {
            return payloadsOf("message.failed").stream()
                    .map(payload -> ((MessageFailed) payload).code())
                    .toList();
        }
    }

    @Test
    @DisplayName("事件顺序：start 先于 delta，delta 先于 end")
    void shouldKeepEventOrder() {
        MessageEntity message = runner.run(request(seat("st_2", "周敏", AgentRoleType.ANALYST)),
                new CancelToken());

        List<String> events = publisher.events;
        assertThat(events.indexOf("message.start")).isLessThan(events.indexOf("message.delta"));
        assertThat(events.indexOf("message.delta")).isLessThan(events.indexOf("message.end"));

        MessageEnd end = (MessageEnd) publisher.payloadsOf("message.end").get(0);
        assertThat(end.messageId()).isEqualTo(message.getId());
        assertThat(end.status()).isEqualTo("DONE");
        assertThat(end.content()).isEqualTo(message.getContent());
    }
}
