package com.panelstudio.application.session;

import com.panelstudio.domain.entity.AgentEntity;
import com.panelstudio.domain.entity.DiscussionEntity;
import com.panelstudio.domain.entity.MessageEntity;
import com.panelstudio.domain.support.UlidGenerator;
import com.panelstudio.infra.config.AppProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * 会话注册表：key 一律是 discussionId（rules/30 §4），
 * 全局并发闸门也放在这里，保证"一场跑满不饿死其他场"。
 */
@Component
public class SessionActorRegistry {

    private final Map<String, SessionActor> actors = new ConcurrentHashMap<>();
    private final Semaphore globalSemaphore;
    private final SessionStore store;
    private final EventPublisher publisher;
    private final AgentRunner agentRunner;
    private final ThreadPoolTaskExecutor executor;
    private final UlidGenerator idGenerator;
    private final AppProperties properties;

    public SessionActorRegistry(SessionStore store,
                                EventPublisher publisher,
                                AgentRunner agentRunner,
                                @Qualifier(com.panelstudio.infra.config.AsyncConfig.AGENT_EXECUTOR)
                                ThreadPoolTaskExecutor executor,
                                UlidGenerator idGenerator,
                                AppProperties properties) {
        this.store = store;
        this.publisher = publisher;
        this.agentRunner = agentRunner;
        this.executor = executor;
        this.idGenerator = idGenerator;
        this.properties = properties;
        this.globalSemaphore = new Semaphore(properties.getConcurrency().getGlobal(), true);
    }

    public SessionActor getOrCreate(DiscussionEntity discussion, List<AgentEntity> seats) {
        return actors.computeIfAbsent(discussion.getId(), key -> {
            List<MessageEntity> history = store.loadHistory(discussion.getId(), 40);
            return new SessionActor(
                    discussion.getId(),
                    discussion.getTopic(),
                    discussion.getGoal(),
                    seats,
                    discussion.getStatus(),
                    discussion.getTurnIndex() == null ? 0 : discussion.getTurnIndex(),
                    store,
                    publisher,
                    agentRunner,
                    executor,
                    idGenerator,
                    new Semaphore(properties.getConcurrency().getPerRoom(), true),
                    globalSemaphore,
                    properties,
                    history);
        });
    }

    public SessionActor get(String discussionId) {
        return actors.get(discussionId);
    }

    public void remove(String discussionId) {
        actors.remove(discussionId);
    }

    public int activeCount() {
        return actors.size();
    }
}
