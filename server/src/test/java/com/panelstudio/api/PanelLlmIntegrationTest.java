package com.panelstudio.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
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
import java.util.List;
import java.util.Map;

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
 * 模型链路：WireMock 模拟 OpenAI 兼容端点，验证「脏 JSON 也能被修复解析」。
 * 返回体故意带 markdown 围栏与尾逗号，覆盖真实模型的常见毛病。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PanelLlmIntegrationTest {

    private static final WireMockServer WIRE_MOCK =
            new WireMockServer(options().dynamicPort());

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
        stubCompletion("争议焦点", """
                {"goal":"判断该不该迁移","disputes":["冷启动数据够不够","迁移成本"],"angles":["成本","技术"],"audience":["决策者"]}
                """);
        stubCompletion("SEAT_INDEX:0", """
                ```json
                {"name":"沈知远","title":"圆桌主持人","stance":"让分歧被看见","focus":["议题拆解","串联"],"voice":"克制、短句","colorToken":"slate",}
                ```
                """);
        stubCompletion("SEAT_INDEX:1", """
                {"name":"陈立","title":"战略顾问","stance":"先算清 ROI 再谈技术","focus":["投入产出","组织成本"],"voice":"先给结论再给数字","colorToken":"amber"}
                """);
        stubCompletion("SEAT_INDEX:2", """
                结果如下：{"name":"周敏","title":"行业分析师","stance":"先看同业曲线","focus":["行业案例","竞品动向"],"voice":"用案例说话","colorToken":"teal"} 以上。
                """);
        stubCompletion("SEAT_INDEX:3", """
                {"name":"林恒","title":"技术专家","stance":"冷启动没有数据就没有召回","focus":["工程可行性","冷启动"],"voice":"直给坑位","colorToken":"indigo"}
                """);
    }

    private void stubCompletion(String bodyMarker, String content) {
        WIRE_MOCK.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlEqualTo("/v1/chat/completions"))
                .withRequestBody(containing(bodyMarker))
                .willReturn(okJson(completion(content))));
    }

    private String completion(String content) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "id", "chatcmpl-test",
                    "choices", List.of(Map.of("index", 0, "message", Map.of("role", "assistant", "content", content))),
                    "usage", Map.of("prompt_tokens", 120, "completion_tokens", 60)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Test
    @DisplayName("走真实模型链路：脏 JSON 被修复后生成 4 个席位并落库")
    void shouldGeneratePanelThroughLlm() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/discussions")
                        .header("X-Device-Id", "llm-device")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "topic", "我们该不该把推荐系统从规则引擎换成向量召回",
                                "panelSize", 4))))
                .andExpect(status().isCreated())
                .andReturn();
        String discussionId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asText();

        MvcResult streamed = mockMvc.perform(post("/api/discussions/" + discussionId + "/panel")
                        .header("X-Device-Id", "llm-device")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .content("{\"regenerate\":false,\"preset\":false}"))
                .andExpect(status().isOk())
                .andReturn();

        String body = "";
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            body = streamed.getResponse().getContentAsString(StandardCharsets.UTF_8);
            if (body.contains("event:panel.done") || body.contains("event:panel.failed")) {
                break;
            }
            Thread.sleep(30);
        }
        assertThat(body).contains("event:panel.analysis").contains("event:panel.done");
        assertThat(body).doesNotContain("panel.failed");

        JsonNode seats = objectMapper.readTree(
                mockMvc.perform(get("/api/discussions/" + discussionId + "/panel"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.seats.length()").value(4))
                        .andReturn().getResponse().getContentAsString()).get("seats");
        assertThat(seats.get(0).get("roleType").asText()).isEqualTo("HOST");
        assertThat(seats.get(0).get("name").asText()).isEqualTo("沈知远");
        assertThat(seats.get(3).get("name").asText()).isEqualTo("林恒");
    }

    @Test
    @DisplayName("模型未命中桩（上游异常）→ panel.failed 且不落半截数据")
    void shouldEmitPanelFailedWhenUpstreamFails() throws Exception {
        WIRE_MOCK.resetAll();
        WIRE_MOCK.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlEqualTo("/v1/chat/completions"))
                .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse().withStatus(500)));

        MvcResult created = mockMvc.perform(post("/api/discussions")
                        .header("X-Device-Id", "llm-device")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "topic", "我们该不该把推荐系统从规则引擎换成向量召回",
                                "panelSize", 4))))
                .andReturn();
        String discussionId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asText();

        MvcResult streamed = mockMvc.perform(post("/api/discussions/" + discussionId + "/panel")
                        .header("X-Device-Id", "llm-device")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .content("{\"regenerate\":false,\"preset\":false}"))
                .andReturn();

        String body = "";
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            body = streamed.getResponse().getContentAsString(StandardCharsets.UTF_8);
            if (body.contains("event:panel.failed")) {
                break;
            }
            Thread.sleep(30);
        }
        assertThat(body).contains("panel.failed").contains("LLM_UPSTREAM_ERROR");
        assertThat(body).doesNotContain("event:seat.added");

        mockMvc.perform(get("/api/discussions/" + discussionId + "/panel"))
                .andExpect(jsonPath("$.seats.length()").value(0));
    }
}
