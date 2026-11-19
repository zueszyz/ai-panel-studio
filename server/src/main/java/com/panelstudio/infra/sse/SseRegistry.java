package com.panelstudio.infra.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.panelstudio.application.MetricsService;
import com.panelstudio.application.session.EventPublisher;
import com.panelstudio.infra.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 单连接多路复用的落点（rules/30 §2）：
 * 以 discussionId 为键分桶，一场可挂多个标签页；每场保留最近 N 帧用于 Last-Event-ID 续传。
 * 禁止出现"全局 emitter 列表"——那必然串台。
 */
@Component
public class SseRegistry implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SseRegistry.class);

    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final Map<String, Deque<SseEvent>> buffers = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> cursors = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final AppProperties properties;
    private final MetricsService metrics;

    public SseRegistry(ObjectMapper objectMapper, AppProperties properties, MetricsService metrics) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.metrics = metrics;
    }

    /**
     * 建立连接：有游标且缓冲足够则补发增量，否则回落为全量快照。
     */
    public SseEmitter register(String discussionId, Long lastEventId, Supplier<Object> snapshotSupplier) {
        SseEmitter emitter = new SseEmitter(0L);
        Runnable cleanup = () -> remove(discussionId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(error -> cleanup.run());

        emitters.computeIfAbsent(discussionId, key -> new CopyOnWriteArrayList<>()).add(emitter);
        metrics.sseConnected();
        log.debug("sse.connect discussionId={} lastEventId={}", discussionId, lastEventId);

        List<SseEvent> replay = replayAfter(discussionId, lastEventId);
        if (replay == null) {
            send(emitter, discussionId, new SseEvent(nextId(discussionId), "snapshot", toJson(snapshotSupplier.get())));
        } else {
            for (SseEvent event : replay) {
                send(emitter, discussionId, event);
            }
            // 回放完再补一帧轻量状态，避免前端停在半截状态
            send(emitter, discussionId, new SseEvent(nextId(discussionId), "discussion.updated",
                    toJson(Map.of("replayed", replay.size()))));
        }
        return emitter;
    }

    @Override
    public void publish(String discussionId, String event, Object payload) {
        String json = toJson(payload);
        SseEvent frame = new SseEvent(nextId(discussionId), event, json);
        Deque<SseEvent> buffer = buffer(discussionId);
        // ArrayDeque 不是线程安全的：并行输出的多个 agent 会同时 publish，
        // 写入与裁剪必须在同一把锁内，否则会丢帧甚至抛 ConcurrentModificationException。
        synchronized (buffer) {
            buffer.addLast(frame);
            trimLocked(buffer);
        }
        List<SseEmitter> targets = emitters.get(discussionId);
        if (targets == null || targets.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : targets) {
            send(emitter, discussionId, frame);
        }
    }

    /** 结束一场：推完最后一帧后关闭所有连接（FR-8：3s 内关闭）。 */
    public void closeAll(String discussionId) {
        List<SseEmitter> targets = emitters.remove(discussionId);
        if (targets == null) {
            return;
        }
        for (SseEmitter emitter : targets) {
            try {
                emitter.complete();
            } catch (RuntimeException ignored) {
                // 已经断开的连接
            }
            metrics.sseDisconnected();
        }
    }

    public int connectionCount(String discussionId) {
        List<SseEmitter> targets = emitters.get(discussionId);
        return targets == null ? 0 : targets.size();
    }

    @Scheduled(fixedRateString = "${panel.sse.heartbeat-ms:15000}")
    public void heartbeat() {
        emitters.forEach((discussionId, targets) -> {
            for (SseEmitter emitter : targets) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (IOException | IllegalStateException ex) {
                    remove(discussionId, emitter);
                }
            }
        });
    }

    private void send(SseEmitter emitter, String discussionId, SseEvent frame) {
        try {
            emitter.send(SseEmitter.event()
                    .id(String.valueOf(frame.id()))
                    .name(frame.type())
                    .data(frame.json(), MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException ex) {
            remove(discussionId, emitter);
        }
    }

    private void remove(String discussionId, SseEmitter emitter) {
        List<SseEmitter> targets = emitters.get(discussionId);
        if (targets != null && targets.remove(emitter)) {
            metrics.sseDisconnected();
            log.debug("sse.disconnect discussionId={} remaining={}", discussionId, targets.size());
        }
    }

    /** 返回 null 表示缓冲不足（或没有游标），调用方回落快照。 */
    private List<SseEvent> replayAfter(String discussionId, Long lastEventId) {
        if (lastEventId == null) {
            return null;
        }
        Deque<SseEvent> buffer = buffers.get(discussionId);
        if (buffer == null) {
            return null;
        }
        synchronized (buffer) {
            if (buffer.isEmpty()) {
                return null;
            }
            List<SseEvent> replay = new ArrayList<>();
            for (SseEvent event : buffer) {
                if (event.id() > lastEventId) {
                    replay.add(event);
                }
            }
            // 缓冲里最老的一帧必须紧跟客户端游标，否则中间已经丢帧，只能回快照
            boolean contiguous = buffer.peekFirst().id() <= lastEventId + 1;
            return contiguous ? replay : null;
        }
    }

    private Deque<SseEvent> buffer(String discussionId) {
        return buffers.computeIfAbsent(discussionId, key -> new ArrayDeque<>());
    }

    /** 调用方必须持有 buffer 的锁。 */
    private void trimLocked(Deque<SseEvent> buffer) {
        int max = properties.getSse().getBufferSize();
        while (buffer.size() > max) {
            buffer.removeFirst();
        }
    }

    private long nextId(String discussionId) {
        return cursors.computeIfAbsent(discussionId, key -> new AtomicLong()).incrementAndGet();
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            log.warn("事件序列化失败 type={}", payload == null ? "null" : payload.getClass().getSimpleName());
            return "{}";
        }
    }

    /** 测试用：清空某场缓冲与连接。 */
    public void reset(String discussionId) {
        closeAll(discussionId);
        buffers.remove(discussionId);
        cursors.remove(discussionId);
    }
}
