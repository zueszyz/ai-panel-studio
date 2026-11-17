package com.panelstudio.application.session;

import com.panelstudio.api.dto.StreamEvents.ErrorFrame;
import com.panelstudio.api.dto.StreamEvents.TurnStart;
import com.panelstudio.domain.entity.AgentEntity;
import com.panelstudio.domain.entity.MessageEntity;
import com.panelstudio.domain.enums.AgentRoleType;
import com.panelstudio.domain.enums.DiscussionStatus;
import com.panelstudio.domain.support.UlidGenerator;
import com.panelstudio.infra.config.AppProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SessionActorTest {

    private final AppProperties properties = new AppProperties();
    private final AgentRunner runner = mock(AgentRunner.class);
    private final CollectingPublisher publisher = new CollectingPublisher();
    private final FakeSessionStore store = new FakeSessionStore();
    private final AtomicInteger sequences = new AtomicInteger();
    private ThreadPoolTaskExecutor executor;
    private SessionActor actor;

    @BeforeEach
    void setUp() {
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.initialize();

        when(runner.run(any(AgentRunner.RunRequest.class), any())).thenAnswer(invocation -> {
            AgentRunner.RunRequest request = invocation.getArgument(0);
            if ("st_2".equals(request.seat().getId()) && properties.getBudget().getDiscussionTokens() < 0) {
                throw new IllegalStateException("不可能发生");
            }
            MessageEntity message = new MessageEntity();
            message.setId("ms_" + sequences.incrementAndGet());
            message.setAgentId(request.seat().getId());
            message.setContent("来自 " + request.seat().getName() + " 的发言");
            message.setTokensOut(40);
            return message;
        });

        actor = new SessionActor("dk_test", "要不要换向量召回", "判断该不该迁移",
                seats(), DiscussionStatus.LIVE, 0, store, publisher, runner, executor,
                new UlidGenerator(), new Semaphore(3), new Semaphore(20), properties, List.of());
    }

    @AfterEach
    void tearDown() {
        executor.shutdown();
    }

    private List<AgentEntity> seats() {
        return List.of(
                seat("st_1", "沈知远", AgentRoleType.HOST, 0),
                seat("st_2", "陈立", AgentRoleType.STRATEGIST, 1),
                seat("st_3", "周敏", AgentRoleType.ANALYST, 2));
    }

    private AgentEntity seat(String id, String name, AgentRoleType role, int order) {
        AgentEntity seat = new AgentEntity();
        seat.setId(id);
        seat.setName(name);
        seat.setTitle("席位");
        seat.setStance("有立场");
        seat.setVoice("短句");
        seat.setFocus(List.of("成本"));
        seat.setRoleType(role);
        seat.setOrderIndex(order);
        seat.setDiscussionId("dk_test");
        return seat;
    }

    @Test
    @DisplayName("开场后自动进入第 1 回合：主持人 + 2 位专家各说一次")
    void shouldOpenThenRunFirstRound() {
        String openingId = actor.startOpening();
        assertThat(openingId).startsWith("ms_");
        assertThat(actor.awaitIdle(5000)).isTrue();

        List<TurnStart> turns = publisher.payloadsOf("turn.start", TurnStart.class);
        assertThat(turns).hasSize(1);
        assertThat(turns.get(0).turnIndex()).isEqualTo(1);
        assertThat(turns.get(0).speakers()).hasSize(2).doesNotContain("st_1");
        assertThat(actor.turnIndex()).isEqualTo(1);
        assertThat(store.turnIndex).isEqualTo(1);
    }

    @Test
    @DisplayName("单席位抛异常不影响同回合其他人，讨论仍是 LIVE")
    void shouldIsolateSeatFailure() {
        when(runner.run(any(AgentRunner.RunRequest.class), any())).thenAnswer(invocation -> {
            AgentRunner.RunRequest request = invocation.getArgument(0);
            if ("st_2".equals(request.seat().getId())) {
                throw new IllegalStateException("模型炸了");
            }
            MessageEntity message = new MessageEntity();
            message.setId("ms_" + sequences.incrementAndGet());
            message.setTokensOut(40);
            return message;
        });

        actor.startOpening();
        assertThat(actor.awaitIdle(5000)).isTrue();

        assertThat(actor.status()).isEqualTo(DiscussionStatus.LIVE);
        assertThat(actor.inflightCount()).isZero();
    }

    @Test
    @DisplayName("预算耗尽后拒绝新发言并推 BUDGET_EXHAUSTED")
    void shouldRejectWhenBudgetExhausted() {
        AppProperties tight = new AppProperties();
        tight.getBudget().setDiscussionTokens(60);
        when(runner.run(any(AgentRunner.RunRequest.class), any())).thenAnswer(invocation -> {
            MessageEntity message = new MessageEntity();
            message.setId("ms_" + sequences.incrementAndGet());
            message.setTokensOut(40);
            return message;
        });
        SessionActor tightActor = new SessionActor("dk_tight", "议题", "目标", seats(),
                DiscussionStatus.LIVE, 0, store, publisher, runner, executor,
                new UlidGenerator(), new Semaphore(3), new Semaphore(20), tight, List.of());

        tightActor.startOpening();
        assertThat(tightActor.awaitIdle(5000)).isTrue();

        MessageEntity user = new MessageEntity();
        user.setId("ms_user");
        user.setContent("那冷启动怎么办？");
        tightActor.onUserMessage(user, null);
        assertThat(tightActor.awaitIdle(3000)).isTrue();

        assertThat(publisher.payloadsOf("error", ErrorFrame.class))
                .anyMatch(frame -> "BUDGET_EXHAUSTED".equals(frame.code()));
        assertThat(tightActor.budget().canSpeak()).isFalse();
    }

    // ── 测试替身 ────────────────────────────────────────────

    private static final class FakeSessionStore implements SessionStore {
        private int turnIndex = -1;
        private DiscussionStatus status;

        @Override
        public List<MessageEntity> loadHistory(String discussionId, int limit) {
            return List.of();
        }

        @Override
        public void updateTurnIndex(String discussionId, int turnIndex) {
            this.turnIndex = turnIndex;
        }

        @Override
        public void updateStatus(String discussionId, DiscussionStatus status, String endedAt) {
            this.status = status;
        }
    }

    private static final class CollectingPublisher implements EventPublisher {
        private final List<String> events = Collections.synchronizedList(new ArrayList<>());
        private final List<Object> payloads = Collections.synchronizedList(new ArrayList<>());
        private final Map<String, List<Object>> byEvent = Collections.synchronizedMap(new java.util.LinkedHashMap<>());

        @Override
        public void publish(String discussionId, String event, Object payload) {
            events.add(event);
            payloads.add(payload);
            byEvent.computeIfAbsent(event, key -> Collections.synchronizedList(new ArrayList<>())).add(payload);
        }

        <T> List<T> payloadsOf(String event, Class<T> type) {
            List<Object> raw = byEvent.getOrDefault(event, List.of());
            synchronized (raw) {
                return raw.stream().filter(type::isInstance).map(type::cast).toList();
            }
        }
    }
}
