package com.panelstudio.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 讨论内核的接口契约：开场 / 事件流 / 幂等 / 状态限制 / 多讨论隔离（I-5 ~ I-7、I-14）。
 * 模型用 WireMock 模拟流式响应：陈立 2s、周敏 200ms，用"谁先 end"证明并行不阻塞。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SessionApiIntegrationTest {

    private static final WireMockServer WIRE_MOCK = new WireMockServer(options().dynamicPort());

    private static final String TOPIC_A = "我们该不该把推荐系统从规则引擎换成向量召回";
    private static final String TOPIC_B = "客服体系要不要先上 AI，人工只做兜底";

    static {
        WIRE_MOCK.start();
    }

    @DynamicPropertySource
    static void llmProperties(DynamicPropertyRegistry registry) {
        registry.add("panel.llm.base-url", () -> WIRE_MOCK.baseUrl() + "/v1");
        registry.add("panel.llm.api-key", () -> "test-key");
        registry.add("panel.llm.model", () -> "test-model");
    }

    @AfterAll
    static void stopWireMock() {
        WIRE_MOCK.stop();
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void resetStubs() {
        WIRE_MOCK.resetAll();
        stubJson("争议焦点", """
                {"goal":"判断该不该迁移","disputes":["冷启动数据够不够","迁移成本能否回收"],"angles":["成本","技术可行性","同业案例"],"audience":["决策者"]}
                """);
        stubJson("SEAT_INDEX:0", """
                {"name":"沈知远","title":"圆桌主持人","stance":"让分歧被看见","focus":["议题拆解","串联"],"voice":"克制","colorToken":"slate"}
                """);
        stubJson("SEAT_INDEX:1", """
                {"name":"陈立","title":"战略顾问","stance":"先算清 ROI","focus":["投入产出","组织成本"],"voice":"先结论后数字","colorToken":"amber"}
                """);
        stubJson("SEAT_INDEX:2", """
                {"name":"周敏","title":"行业分析师","stance":"先看同业曲线","focus":["行业案例","竞品动向"],"voice":"用案例说话","colorToken":"teal"}
                """);
        stubJson("SEAT_INDEX:3", """
                {"name":"林恒","title":"技术专家","stance":"冷启动没有数据就没有召回","focus":["工程可行性","冷启动"],"voice":"直给坑位","colorToken":"indigo"}
                """);

        // 流式发言：主持人开场、两位专家（一慢一快）
        stubStream("你是这场圆桌的主持人沈知远", "各位好，今天只回答一个问题：该不该迁移。", 6, 300);
        stubStream("你叫陈立", "先算清迁移成本，再谈技术先进性，这是顺序问题。", 10, 2000);
        stubStream("你叫周敏", "同业里三家换了向量召回，两家半年后回滚，案例摆在这里。", 6, 200);
        stubStream("你叫林恒", "冷启动没有行为数据，向量召回拿什么训练，这是硬约束。", 6, 200);
    }

    private void stubJson(String marker, String content) {
        WIRE_MOCK.stubFor(WireMock.post(urlEqualTo("/v1/chat/completions"))
                .withRequestBody(containing(marker))
                .willReturn(okJson(completion(content))));
    }

    private void stubStream(String marker, String text, int chunks, int totalMillis) {
        WIRE_MOCK.stubFor(WireMock.post(urlEqualTo("/v1/chat/completions"))
                .withRequestBody(containing(marker))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.TEXT_EVENT_STREAM_VALUE)
                        .withBody(sseBody(text, chunks))
                        .withChunkedDribbleDelay(chunks, totalMillis)));
    }

    /** 把文本按词切成 N 段，模拟逐 token 下发。 */
    private String sseBody(String text, int chunks) {
        StringBuilder body = new StringBuilder();
        int size = Math.max(1, text.length() / chunks + 1);
        for (int index = 0; index < text.length(); index += size) {
            String piece = text.substring(index, Math.min(text.length(), index + size));
            body.append("data: ").append(deltaJson(piece)).append("\n\n");
        }
        body.append("data: [DONE]\n\n");
        return body.toString();
    }

    private String deltaJson(String piece) {
        try {
            return objectMapper.writeValueAsString(Map.of("choices",
                    List.of(Map.of("index", 0, "delta", Map.of("content", piece)))));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String completion(String content) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "choices", List.of(Map.of("index", 0,
                            "message", Map.of("role", "assistant", "content", content))),
                    "usage", Map.of("prompt_tokens", 100, "completion_tokens", 50)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    // ── 用例 ────────────────────────────────────────────────

    @Test
    @DisplayName("开场后推动第 1 回合：慢席位不阻塞快席位（message.end 先后可证）")
    void shouldStreamOpeningAndParallelRound() throws Exception {
        String discussionId = createLiveReadyPanel(TOPIC_A);
        // 与真实前端一致：先连流，再开场（否则 snapshot 之后的事件会漏）
        MvcResult stream = mockMvc.perform(get("/api/discussions/" + discussionId + "/stream")
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        awaitStream(stream, "event:snapshot", 1, 10_000);

        mockMvc.perform(post("/api/discussions/" + discussionId + "/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LIVE"))
                .andExpect(jsonPath("$.openingMessageId").exists());

        // 1) 开场：主持人先说话
        String opening = awaitStream(stream, "event:message.end", 1, 20_000);
        assertThat(opening).contains("event:snapshot").contains("event:message.start");

        // 2) 点名陈立，逼他被选中（其他席位没有加分项）
        mockMvc.perform(post("/api/discussions/" + discussionId + "/messages")
                        .header("X-Device-Id", "device-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "content", "陈立，迁移成本这么大，谁来兜底？",
                                "clientMsgId", "call-chenli"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expectedResponders").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        // 3) 等到"用户消息 + 陈立回复"都结束
        Map<String, String> seatNames = seatNames(discussionId);
        String body = awaitUntil(stream, current -> {
            List<String> names = endNames(current, seatNames);
            return names.contains("你") && names.contains("陈立");
        }, 25_000);

        List<String> endOrder = endNames(body, seatNames);
        int userIndex = endOrder.lastIndexOf("你");
        List<String> replies = endOrder.subList(userIndex + 1, endOrder.size());
        assertThat(replies).contains("陈立");
        // 陈立 2s、其他人 200ms：慢的不该排在快的前面完成
        assertThat(replies.get(replies.size() - 1)).isEqualTo("陈立");
        assertThat(body).contains("event:turn.start").contains("event:message.delta");
    }

    @Test
    @DisplayName("I-5 同一 clientMsgId 重复提交只落一条、不重复调度")
    void shouldBeIdempotentOnClientMsgId() throws Exception {
        String discussionId = createLiveReadyPanel(TOPIC_A);
        mockMvc.perform(post("/api/discussions/" + discussionId + "/start")).andExpect(status().isOk());

        String payload = objectMapper.writeValueAsString(Map.of(
                "content", "冷启动没有行为数据，向量召回拿什么训练？",
                "clientMsgId", "client-1"));

        MvcResult first = mockMvc.perform(post("/api/discussions/" + discussionId + "/messages")
                        .header("X-Device-Id", "device-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enqueued").value(true))
                .andExpect(jsonPath("$.expectedResponders").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andReturn();

        MvcResult second = mockMvc.perform(post("/api/discussions/" + discussionId + "/messages")
                        .header("X-Device-Id", "device-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enqueued").value(false))
                .andReturn();

        String firstId = objectMapper.readTree(first.getResponse().getContentAsString()).path("id").asText();
        String secondId = objectMapper.readTree(second.getResponse().getContentAsString()).path("id").asText();
        assertThat(secondId).isEqualTo(firstId);
    }

    @Test
    @DisplayName("I-6 非 LIVE 发言 409；I-7 结束后发言 410")
    void shouldRejectPostOutsideLive() throws Exception {
        String draftId = createDraft();
        mockMvc.perform(post("/api/discussions/" + draftId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "content", "还没开场就想说话", "clientMsgId", "c-draft"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DISCUSSION_NOT_LIVE"));

        String liveId = createLiveReadyPanel(TOPIC_A);
        mockMvc.perform(post("/api/discussions/" + liveId + "/start")).andExpect(status().isOk());
        mockMvc.perform(post("/api/discussions/" + liveId + "/end"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUMMARIZING"));

        mockMvc.perform(post("/api/discussions/" + liveId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "content", "结束了还要说话", "clientMsgId", "c-closed"))))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error.code").value("DISCUSSION_CLOSED"));

        mockMvc.perform(post("/api/discussions/" + liveId + "/start"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DISCUSSION_CLOSED"));
    }

    @Test
    @DisplayName("I-14 两场并行：向 A 发消息，B 的事件流里收不到任何 A 的内容")
    void shouldIsolateDiscussions() throws Exception {
        String a = createLiveReadyPanel(TOPIC_A);
        String b = createLiveReadyPanel(TOPIC_B);
        mockMvc.perform(post("/api/discussions/" + a + "/start")).andExpect(status().isOk());
        mockMvc.perform(post("/api/discussions/" + b + "/start")).andExpect(status().isOk());

        MvcResult streamA = mockMvc.perform(get("/api/discussions/" + a + "/stream")
                .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)).andReturn();
        MvcResult streamB = mockMvc.perform(get("/api/discussions/" + b + "/stream")
                .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)).andReturn();
        awaitStream(streamB, "event:snapshot", 1, 10_000);

        MvcResult posted = mockMvc.perform(post("/api/discussions/" + a + "/messages")
                        .header("X-Device-Id", "device-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "content", "只有 A 该看到的插话", "clientMsgId", "iso-1"))))
                .andExpect(status().isOk())
                .andReturn();
        String messageId = objectMapper.readTree(posted.getResponse().getContentAsString()).path("id").asText();

        String bodyA = awaitStream(streamA, messageId, 1, 15_000);
        String bodyB = streamB.getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(bodyA).contains(messageId).contains("只有 A 该看到的插话");
        assertThat(bodyB).doesNotContain(messageId).doesNotContain("只有 A 该看到的插话").doesNotContain(TOPIC_A);
    }

    @Test
    @DisplayName("席位 focus 与轨迹 meta 可回读（手写 SQL 不能丢 JSON 列）")
    void shouldReadBackJsonColumns() throws Exception {
        String discussionId = createLiveReadyPanel(TOPIC_A);

        JsonNode seats = objectMapper.readTree(mockMvc.perform(get("/api/discussions/" + discussionId + "/panel")
                        .header("X-Device-Id", "device-a"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).get("seats");
        assertThat(seats.get(1).get("focus").size()).isGreaterThan(0);

        mockMvc.perform(post("/api/discussions/" + discussionId + "/start")).andExpect(status().isOk());

        JsonNode items = null;
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            MvcResult runs = mockMvc.perform(get("/api/discussions/" + discussionId + "/runs"))
                    .andExpect(status().isOk())
                    .andReturn();
            items = objectMapper.readTree(runs.getResponse().getContentAsString(StandardCharsets.UTF_8))
                    .get("items");
            if (items != null && items.size() > 0) {
                break;
            }
            Thread.sleep(50);
        }
        assertThat(items).as("开场后应至少落一条轨迹").isNotNull();
        assertThat(items.size()).isGreaterThan(0);
        assertThat(items.get(0).get("meta").has("kind")).as("轨迹 meta 不能是空对象").isTrue();
    }

    @Test
    @DisplayName("FR-9 单条重试：失败的发言沿用同一 messageId 重新生成，卡片可回到 DONE")
    void shouldRetryFailedMessage() throws Exception {
        String discussionId = createLiveReadyPanel(TOPIC_A);
        mockMvc.perform(post("/api/discussions/" + discussionId + "/start")).andExpect(status().isOk());

        // 开场会自动跑一轮，陈立可能已经发过言；故障桩必须等自动轮收尾后再注册，
        // 否则第一次 500 会被自动轮消费掉，重试用例拿不到失败轨迹。
        long settleDeadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < settleDeadline) {
            JsonNode items = objectMapper.readTree(mockMvc.perform(get("/api/discussions/" + discussionId + "/runs"))
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).path("items");
            int done = 0;
            for (JsonNode run : items) {
                if ("DONE".equals(run.path("phase").asText())
                        && "SPEECH".equals(run.path("meta").path("kind").asText())) {
                    done++;
                }
            }
            if (done >= 3) {
                break;
            }
            Thread.sleep(50);
        }
        WIRE_MOCK.stubFor(WireMock.post(urlEqualTo("/v1/chat/completions"))
                .atPriority(1)
                .inScenario("retry-chenli")
                .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                .withRequestBody(containing("你叫陈立"))
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("RECOVERED"));
        WIRE_MOCK.stubFor(WireMock.post(urlEqualTo("/v1/chat/completions"))
                .atPriority(1)
                .inScenario("retry-chenli")
                .whenScenarioStateIs("RECOVERED")
                .withRequestBody(containing("你叫陈立"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.TEXT_EVENT_STREAM_VALUE)
                        .withBody(sseBody("重试之后，我给的结论是先设三周判断点。", 5))
                        .withChunkedDribbleDelay(5, 200)));

        mockMvc.perform(post("/api/discussions/" + discussionId + "/messages")
                        .header("X-Device-Id", "device-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "content", "陈立，先说你最担心哪一步？",
                                "clientMsgId", "retry-1"))))
                .andExpect(status().isOk());

        // 等陈立这条落成 FAILED
        String failedMessageId = null;
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline && failedMessageId == null) {
            JsonNode items = objectMapper.readTree(mockMvc.perform(get("/api/discussions/" + discussionId + "/runs"))
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).path("items");
            for (JsonNode run : items) {
                // 只挑专家发言（DIGEST 这类收束这段 stub 没有覆盖，不是本用例要验证的路径）
                if ("FAILED".equals(run.path("phase").asText())
                        && "SPEECH".equals(run.path("meta").path("kind").asText())
                        && !run.path("messageId").isNull()) {
                    failedMessageId = run.path("messageId").asText();
                }
            }
            if (failedMessageId == null) {
                Thread.sleep(50);
            }
        }
        assertThat(failedMessageId).as("应有一条失败轨迹").isNotNull();

        mockMvc.perform(post("/api/discussions/" + discussionId + "/messages/" + failedMessageId + "/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(failedMessageId))
                .andExpect(jsonPath("$.status").value("STREAMING"));

        // 重试后同一条消息应变成 DONE（用轨迹判断，避免反复建 SSE 连接）
        boolean done = false;
        deadline = System.currentTimeMillis() + 20_000;
        String diagnostic = "";
        while (System.currentTimeMillis() < deadline && !done) {
            MvcResult runs = mockMvc.perform(get("/api/discussions/" + discussionId + "/runs"))
                    .andExpect(status().isOk())
                    .andReturn();
            diagnostic = runs.getResponse().getContentAsString(StandardCharsets.UTF_8);
            for (JsonNode run : objectMapper.readTree(diagnostic).path("items")) {
                if (failedMessageId.equals(run.path("messageId").asText())
                        && "DONE".equals(run.path("phase").asText())) {
                    done = true;
                }
            }
            if (!done) {
                Thread.sleep(100);
            }
        }
        assertThat(done).withFailMessage("重试后同一条消息应变成 DONE，轨迹：%s", diagnostic).isTrue();
    }

    // ── 辅助 ────────────────────────────────────────────────

    private String createDraft() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/discussions")
                        .header("X-Device-Id", "device-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("topic", TOPIC_A, "panelSize", 4))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).path("id").asText();
    }

    private String createLiveReadyPanel(String topic) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/discussions")
                        .header("X-Device-Id", "device-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("topic", topic, "panelSize", 4))))
                .andExpect(status().isCreated())
                .andReturn();
        String discussionId = objectMapper.readTree(created.getResponse().getContentAsString()).path("id").asText();

        MvcResult panel = mockMvc.perform(post("/api/discussions/" + discussionId + "/panel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .content("{\"regenerate\":false,\"preset\":false}"))
                .andExpect(status().isOk())
                .andReturn();
        String panelBody = awaitStream(panel, "event:panel.done", 1, 20_000);
        assertThat(panelBody)
                .withFailMessage("组局没成功，流内容：%s", panelBody)
                .contains("event:panel.done");
        return discussionId;
    }

    private Map<String, String> seatNames(String discussionId) throws Exception {
        JsonNode panel = objectMapper.readTree(mockMvc.perform(get("/api/discussions/" + discussionId + "/panel"))
                .andReturn().getResponse().getContentAsString());
        Map<String, String> names = new LinkedHashMap<>();
        panel.path("seats").forEach(seat -> names.put(seat.path("id").asText(), seat.path("name").asText()));
        return names;
    }

    /** message.end 的到达顺序（映射成席位姓名，用户消息记为「你」）。 */
    private List<String> endNames(String body, Map<String, String> seatNames) {
        Map<String, String> messageSeat = messageSeatMap(body);
        return eventsWithPayload(body, "message.end").stream()
                .map(json -> {
                    String seatId = messageSeat.get(json.path("messageId").asText());
                    return seatId == null ? "你" : seatNames.getOrDefault(seatId, "?");
                })
                .toList();
    }

    private Map<String, String> messageSeatMap(String body) {
        Map<String, String> map = new LinkedHashMap<>();
        for (JsonNode payload : eventsWithPayload(body, "message.start")) {
            map.put(payload.path("messageId").asText(), payload.path("seatId").asText(null));
        }
        return map;
    }

    private String awaitUntil(MvcResult result, java.util.function.Predicate<String> condition,
                              long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        String body = "";
        while (System.currentTimeMillis() < deadline) {
            body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
            if (condition.test(body)) {
                return body;
            }
            Thread.sleep(50);
        }
        return body;
    }

    private String awaitStream(MvcResult result, String marker, int expected, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        String body = "";
        while (System.currentTimeMillis() < deadline) {
            body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
            if (countOf(body, marker) >= expected) {
                return body;
            }
            Thread.sleep(50);
        }
        return body;
    }

    private int countOf(String body, String marker) {
        int count = 0;
        int index = body.indexOf(marker);
        while (index >= 0) {
            count++;
            index = body.indexOf(marker, index + marker.length());
        }
        return count;
    }

    /** 抽取某类事件的全部 data JSON（顺序即到达顺序）。 */
    private List<JsonNode> eventsWithPayload(String body, String event) {
        List<JsonNode> payloads = new ArrayList<>();
        String[] blocks = body.split("\n\n");
        for (String block : blocks) {
            if (!block.contains("event:" + event + "\n")) {
                continue;
            }
            for (String line : block.split("\n")) {
                if (line.startsWith("data:")) {
                    try {
                        payloads.add(objectMapper.readTree(line.substring(5).trim()));
                    } catch (Exception ignored) {
                        // 忽略非 JSON 的注释帧
                    }
                }
            }
        }
        return payloads;
    }

    private List<String> events(String body, String event) {
        return eventsWithPayload(body, event).stream().map(JsonNode::toString).toList();
    }
}
