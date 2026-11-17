package com.panelstudio.application.session;

/**
 * 事件出口：业务层只描述"发生了什么"，由基础设施层决定推给哪些连接。
 * 这样 SessionActor / AgentRunner 可以在没有 Web 层的情况下单测。
 */
public interface EventPublisher {

    void publish(String discussionId, String event, Object payload);
}
