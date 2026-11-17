package com.panelstudio.infra.sse;

/**
 * 一帧事件：id 为单调游标（Last-Event-ID 续传用），data 已序列化为 JSON。
 */
public record SseEvent(long id, String type, String json) {
}
