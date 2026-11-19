package com.panelstudio.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.panelstudio.application.session.EventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** I-11：带 Last-Event-ID 重连时回放未收到的帧，而不是回落全量快照。 */
@SpringBootTest(properties = "panel.sse.replay-test=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SseReplayIntegrationTest {

    private static final String TOPIC = "我们该不该把推荐系统从规则引擎换成向量召回";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EventPublisher publisher;

    @Test
    @DisplayName("I-11 Last-Event-ID=1 重连：只回放之后的两帧，不重复已收帧")
    void shouldReplayOnlyFramesAfterCursor() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/discussions")
                        .header("X-Device-Id", "device-sse-replay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("topic", TOPIC, "panelSize", 4))))
                .andExpect(status().isCreated())
                .andReturn();
        String id = objectMapper.readTree(created.getResponse().getContentAsString()).path("id").asText();

        // 缓冲内依次为 id=1,2,3；客户端已收到 1，应只回放 2、3。
        publisher.publish(id, "message.delta", Map.of("seq", 1, "text", "first"));
        publisher.publish(id, "message.delta", Map.of("seq", 2, "text", "second"));
        publisher.publish(id, "message.end", Map.of("status", "DONE"));

        MvcResult stream = mockMvc.perform(get("/api/discussions/" + id + "/stream")
                        .header("X-Device-Id", "device-sse-replay")
                        .header("Last-Event-ID", "1")
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        String body = awaitBody(stream, "event:discussion.updated");
        assertThat(body)
                .contains("event:message.delta")
                .contains("event:message.end")
                .contains("\"replayed\":2")
                .doesNotContain("event:snapshot")
                .doesNotContain("\"text\":\"first\"");
        assertThat(body).contains("\"text\":\"second\"");
    }

    private String awaitBody(MvcResult result, String marker) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        String body = "";
        while (System.currentTimeMillis() < deadline) {
            body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
            if (body.contains(marker)) {
                return body;
            }
            Thread.sleep(50);
        }
        throw new IllegalStateException("没等到回放完成帧：" + marker + "；响应：" + body);
    }
}
