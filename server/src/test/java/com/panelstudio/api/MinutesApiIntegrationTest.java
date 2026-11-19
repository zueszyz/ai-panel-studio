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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 * 收敛模块的接口契约（PRD FR-7 / I-8 / I-9）：
 * 六段分节到达、版本自增、失败不覆盖旧版本、未结束不能出正式纪要。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MinutesApiIntegrationTest {

    private static final WireMockServer WIRE_MOCK = new WireMockServer(options().dynamicPort());
    private static final String TOPIC = "我们该不该把推荐系统从规则引擎换成向量召回";
    private static final String MINUTES_MARKER = "你是这场圆桌会议的主笔";

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
    void stubLlm() {
        WIRE_MOCK.resetAll();
        stubJson("争议焦点", """
                {"goal":"判断该不该迁移","disputes":["冷启动数据够不够","迁移成本能否回收"],"angles":["成本","技术可行性"],"audience":["决策者"]}
                """, 2);
        stubJson("SEAT_INDEX:0", """
                {"name":"沈知远","title":"圆桌主持人","stance":"让分歧被看见","focus":["议题拆解","串联"],"voice":"克制","colorToken":"slate"}
                """, 2);
        stubJson("SEAT_INDEX:1", """
                {"name":"陈立","title":"战略顾问","stance":"先算清 ROI","focus":["投入产出","组织成本"],"voice":"先结论后数字","colorToken":"amber"}
                """, 2);
        stubJson("SEAT_INDEX:2", """
                {"name":"周敏","title":"行业分析师","stance":"先看同业曲线","focus":["行业案例","竞品动向"],"voice":"用案例说话","colorToken":"teal"}
                """, 2);
        stubJson("SEAT_INDEX:3", """
                {"name":"林恒","title":"技术专家","stance":"冷启动没有数据就没有召回","focus":["冷启动","工程可行性"],"voice":"直给坑位","colorToken":"indigo"}
                """, 2);
        stubStream("你是这场圆桌的主持人", "各位好，今天只回答一个问题：该不该迁移。", 3, 120);
        stubStream("你叫陈立", "先算清迁移成本，再谈技术先进性。", 3, 120);
        stubStream("你叫周敏", "同业里三家换了向量召回，两家回滚。", 3, 120);
        stubStream("你叫林恒", "冷启动没有行为数据，向量召回拿什么训练。", 3, 120);
        stubValidMinutes();
    }

    // ── 用例 ────────────────────────────────────────────────

    @Test
    @DisplayName("I-8 连续生成两份纪要：版本自增，旧版本可按版本号取回")
    void shouldVersionMinutes() throws Exception {
        String discussionId = createEndedDiscussion();

        String firstStream = generateMinutes(discussionId, "event:minutes.done");
        assertThat(firstStream)
                .withFailMessage("第一次生成没完成，流内容：%s", firstStream)
                .contains("event:minutes.section").contains("event:minutes.done");

        JsonNode first = fetchMinutes(discussionId, null);
        assertThat(first.path("version").asInt()).isEqualTo(1);
        assertThat(first.path("status").asText()).isEqualTo("READY");
        assertThat(first.path("structured").path("claims").size()).isGreaterThan(0);
        assertThat(first.path("versions").size()).isEqualTo(1);

        generateMinutes(discussionId, "event:minutes.done");
        JsonNode second = fetchMinutes(discussionId, null);
        assertThat(second.path("version").asInt()).isEqualTo(2);

        JsonNode oldVersion = fetchMinutes(discussionId, 1);
        assertThat(oldVersion.path("version").asInt()).isEqualTo(1);

        // 溯源 id 必须属于本场，不能把别场消息写进纪要
        Set<String> ownMessageIds = messageIds(discussionId);
        for (JsonNode claim : second.path("structured").path("claims")) {
            for (JsonNode source : claim.path("sourceMessageIds")) {
                assertThat(ownMessageIds).contains(source.asText());
            }
        }
    }

    @Test
    @DisplayName("纪要补参会事实：席位名单、回合/消息数与用户关切来自本场数据")
    void shouldIncludeParticipantFactsAndUserQuestions() throws Exception {
        String discussionId = createLiveDiscussion();
        String question = "迁移成本按什么口径算？";
        mockMvc.perform(post("/api/discussions/" + discussionId + "/messages")
                        .header("X-Device-Id", "minutes-device")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "content", question,
                                "clientMsgId", "minutes-question-" + discussionId))))
                .andExpect(status().isOk());
        // 等这一轮发言真正收尾再结束：否则残留的模型调用会占住执行器，拖慢后续用例。
        awaitUserReply(discussionId);
        mockMvc.perform(post("/api/discussions/" + discussionId + "/end")).andExpect(status().isOk());

        generateMinutes(discussionId, "event:minutes.done");
        JsonNode meta = fetchMinutes(discussionId, null).path("structured").path("meta");
        assertThat(meta.path("seatCount").asInt()).isEqualTo(4);
        assertThat(meta.path("messageCount").asInt()).isGreaterThan(0);
        assertThat(meta.path("participants").size()).isEqualTo(4);
        assertThat(meta.path("userQuestions").toString()).contains(question);
    }

    @Test
    @DisplayName("I-9 生成失败不覆盖上一版：旧版本仍可读，响应带 keptVersion")
    void shouldKeepPreviousVersionOnFailure() throws Exception {
        String discussionId = createEndedDiscussion();
        generateMinutes(discussionId, "event:minutes.done");
        assertThat(fetchMinutes(discussionId, null).path("version").asInt()).isEqualTo(1);

        // 让模型连续两次返回非 JSON：服务端重问一次后放弃（priority 0 盖过正常 stub）
        WIRE_MOCK.stubFor(WireMock.post(urlEqualTo("/v1/chat/completions"))
                .atPriority(0)
                .withRequestBody(containing(MINUTES_MARKER))
                .willReturn(okJson(completion("这不是 JSON，而是一段散文", 2))));

        String failedStream = generateMinutes(discussionId, "event:minutes.failed");
        assertThat(failedStream).contains("event:minutes.failed").contains("keptVersion");

        JsonNode kept = fetchMinutes(discussionId, null);
        assertThat(kept.path("version").asInt()).as("失败不该产生新版本").isEqualTo(1);
        assertThat(kept.path("status").asText()).isEqualTo("READY");
    }

    @Test
    @DisplayName("讨论未结束时不能生成正式纪要（409），没有纪要时 GET 返回 404")
    void shouldRejectMinutesBeforeDiscussionEnds() throws Exception {
        String draftId = createDraft();
        mockMvc.perform(get("/api/discussions/" + draftId + "/minutes"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MINUTES_NOT_FOUND"));

        mockMvc.perform(post("/api/discussions/" + draftId + "/minutes")
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DISCUSSION_STATE_INVALID"));
    }

    @Test
    @DisplayName("FR-7 阶段性小结：讨论中可流式生成，但不落库、不占版本、不结束讨论")
    void shouldGenerateInterimSummaryWithoutPersisting() throws Exception {
        String discussionId = createLiveDiscussion();

        mockMvc.perform(post("/api/discussions/" + discussionId + "/minutes")
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DISCUSSION_STATE_INVALID"));

        String stream = generateMinutes(discussionId, "event:minutes.done", true);
        assertThat(stream)
                .contains("event:minutes.section")
                .contains("event:minutes.done")
                .contains("\"interim\":true");

        mockMvc.perform(get("/api/discussions/" + discussionId + "/minutes"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MINUTES_NOT_FOUND"));
        mockMvc.perform(get("/api/discussions/" + discussionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LIVE"));
    }

    // ── 辅助 ────────────────────────────────────────────────

    private void stubJson(String marker, String content, int priority) {
        WIRE_MOCK.stubFor(WireMock.post(urlEqualTo("/v1/chat/completions"))
                .atPriority(priority)
                .withRequestBody(containing(marker))
                .willReturn(okJson(completion(content, 2))));
    }

    private void stubValidMinutes() {
        String structured = """
                {"topic":"%s","goal":"判断该不该迁移",
                 "claims":[{"seatId":"SEAT_PLACEHOLDER","name":"陈立","points":["全量替换不划算","先设三周判断点"],"sourceMessageIds":["MSG_PLACEHOLDER"]}],
                 "disputes":[{"question":"冷启动数据是否足够","sideA":{"seats":["SEAT_PLACEHOLDER"],"argument":"缺行为数据","sources":["MSG_PLACEHOLDER"]},
                              "sideB":{"seats":["SEAT_PLACEHOLDER"],"argument":"可以先做影子流量","sources":["MSG_PLACEHOLDER"]},
                              "unresolvedBecause":"缺少 A/B 数据"}],
                 "consensus":["不做全量替换"],
                 "hypotheses":["若三周内 CTR 提升低于 3%% 则方案不成立"],
                 "nextSteps":[{"action":"开影子实验","proposedBy":"周敏"}]}
                """.formatted(TOPIC);
        // 席位与消息 id 在场次创建后才确定，这里用宽松占位：清洗阶段会把不认识的 id 剔除，
        // 因此另有一条「真实 id」的 stub 在真机路径下不参与断言（见 shouldVersionMinutes）。
        WIRE_MOCK.stubFor(WireMock.post(urlEqualTo("/v1/chat/completions"))
                .atPriority(1)
                .withRequestBody(containing(MINUTES_MARKER))
                .willReturn(okJson(completion(structured, 2))));
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

    private String completion(String content, int completionTokens) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "choices", List.of(Map.of("index", 0,
                            "message", Map.of("role", "assistant", "content", content))),
                    "usage", Map.of("prompt_tokens", 100, "completion_tokens", completionTokens)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String createDraft() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/discussions")
                        .header("X-Device-Id", "device-minutes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("topic", TOPIC, "panelSize", 4))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).path("id").asText();
    }

    /** 建一场已经开场的讨论：席位已就绪 + 至少一条主持人发言。 */
    /** 等到用户插话的回应落库，避免测试结束时还有模型调用在跑。 */
    private void awaitUserReply(String discussionId) throws Exception {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            MvcResult result = mockMvc.perform(get("/api/discussions/" + discussionId + "/runs"))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode items = objectMapper.readTree(
                    result.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("items");
            boolean busy = false;
            boolean replied = false;
            for (JsonNode item : items) {
                String phase = item.path("phase").asText("");
                if (!"DONE".equals(phase) && !"FAILED".equals(phase)) {
                    busy = true;
                }
                if ("USER_INTERRUPT".equals(item.path("trigger").asText())
                        && ("DONE".equals(phase) || "FAILED".equals(phase))) {
                    replied = true;
                }
            }
            if (replied && !busy) {
                return;
            }
            Thread.sleep(100);
        }
    }

    private String createLiveDiscussion() throws Exception {
        String discussionId = createDraft();
        MvcResult panel = mockMvc.perform(post("/api/discussions/" + discussionId + "/panel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .content("{\"regenerate\":false,\"preset\":false}"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(awaitStream(panel, "event:panel.done", 20_000)).contains("event:panel.done");

        MvcResult stream = mockMvc.perform(get("/api/discussions/" + discussionId + "/stream")
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        mockMvc.perform(post("/api/discussions/" + discussionId + "/start")).andExpect(status().isOk());
        assertThat(awaitStream(stream, "event:message.end", 20_000))
                .withFailMessage("开场发言没完成")
                .contains("event:message.end");
        return discussionId;
    }

    private String createEndedDiscussion() throws Exception {
        String discussionId = createLiveDiscussion();
        mockMvc.perform(post("/api/discussions/" + discussionId + "/end")).andExpect(status().isOk());
        return discussionId;
    }

    private String generateMinutes(String discussionId, String doneMarker) throws Exception {
        return generateMinutes(discussionId, doneMarker, false);
    }

    private String generateMinutes(String discussionId, String doneMarker, boolean interim) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/discussions/" + discussionId + "/minutes")
                        .param("interim", String.valueOf(interim))
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        return awaitStream(result, doneMarker, 20_000);
    }

    private JsonNode fetchMinutes(String discussionId, Integer version) throws Exception {
        var request = get("/api/discussions/" + discussionId + "/minutes");
        if (version != null) {
            request = request.param("version", String.valueOf(version));
        }
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private Set<String> messageIds(String discussionId) throws Exception {
        MvcResult stream = mockMvc.perform(get("/api/discussions/" + discussionId + "/stream")
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode snapshot = awaitSnapshot(stream);
        Set<String> ids = new HashSet<>();
        snapshot.path("messages").forEach(message -> ids.add(message.path("id").asText()));
        return ids;
    }

    private JsonNode awaitSnapshot(MvcResult result) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
            for (String block : body.split("\n\n")) {
                if (!block.contains("event:snapshot\n")) {
                    continue;
                }
                for (String line : block.split("\n")) {
                    if (line.startsWith("data:")) {
                        return objectMapper.readTree(line.substring(5).trim());
                    }
                }
            }
            Thread.sleep(50);
        }
        throw new IllegalStateException("没等到 snapshot");
    }

    private String awaitStream(MvcResult result, String marker, long timeoutMs) throws Exception {        long deadline = System.currentTimeMillis() + timeoutMs;
        String body = "";
        while (System.currentTimeMillis() < deadline) {
            body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
            if (body.contains(marker)) {
                return body;
            }
            Thread.sleep(50);
        }
        return body;
    }
}
