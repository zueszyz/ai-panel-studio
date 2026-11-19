package com.panelstudio.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 组局契约（不依赖模型，全部走预设阵容）：
 * I-2 帧序、I-3 重复调用不重建、I-4 认领冲突与撤销。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PanelApiIntegrationTest {

    private static final String DEVICE_A = "panel-device-a";
    private static final String DEVICE_B = "panel-device-b";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String createDiscussion() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/discussions")
                        .header("X-Device-Id", DEVICE_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "topic", "我们要不要把这个季度的人效指标改成人均产出",
                                "panelSize", 4))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String generatePanel(String discussionId, boolean regenerate, boolean preset) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/discussions/" + discussionId + "/panel")
                        .header("X-Device-Id", DEVICE_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "regenerate", regenerate, "preset", preset))))
                .andExpect(request().asyncStarted())
                .andReturn();

        // SSE 是逐帧写入的：轮询到终止帧出现（不 sleep 固定时长）
        String body = "";
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
            if (body.contains("event:panel.done") || body.contains("event:panel.failed")) {
                break;
            }
            Thread.sleep(30);
        }
        assertThat(body).contains("panel.done");
        return body;
    }

    private List<String> eventNames(String body) {
        List<String> names = new ArrayList<>();
        for (String line : body.split("\n")) {
            if (line.startsWith("event:")) {
                names.add(line.substring("event:".length()).trim());
            }
        }
        return names;
    }

    private JsonNode seatIdsFrom(String body) throws Exception {
        List<JsonNode> seats = new ArrayList<>();
        for (String frame : body.split("\n\n")) {
            if (frame.contains("event:seat.added")) {
                String data = frame.substring(frame.indexOf("data:") + "data:".length()).trim();
                seats.add(objectMapper.readTree(data));
            }
        }
        return objectMapper.valueToTree(seats);
    }

    @Test
    @DisplayName("I-2：预设阵容帧序为 panel.analysis → seat.added ×4 → panel.done")
    void shouldStreamPanelInOrder() throws Exception {
        String discussionId = createDiscussion();
        String body = generatePanel(discussionId, false, true);

        List<String> events = eventNames(body);
        assertThat(events).isNotEmpty();
        assertThat(events.get(0)).isEqualTo("panel.analysis");
        assertThat(events.get(events.size() - 1)).isEqualTo("panel.done");
        assertThat(events.stream().filter("seat.added"::equals)).hasSize(4);
        assertThat(events).noneMatch("panel.failed"::equals);

        JsonNode seats = seatIdsFrom(body);
        assertThat(seats.get(0).get("roleType").asText()).isEqualTo("HOST");
        assertThat(seats.get(0).get("orderIndex").asInt()).isZero();
    }

    @Test
    @DisplayName("面板生成后状态变为 PANEL_READY，GET /panel 可恢复席位")
    void shouldPersistPanel() throws Exception {
        String discussionId = createDiscussion();
        generatePanel(discussionId, false, true);

        mockMvc.perform(get("/api/discussions/" + discussionId + "/panel")
                        .header("X-Device-Id", DEVICE_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PANEL_READY"))
                .andExpect(jsonPath("$.seats.length()").value(4));

        mockMvc.perform(get("/api/discussions/" + discussionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PANEL_READY"))
                .andExpect(jsonPath("$.topicAnalysis.disputes").isArray())
                .andExpect(jsonPath("$.goal").isNotEmpty());
    }

    @Test
    @DisplayName("I-3：已有 PANEL_READY 时再次调用不重建席位（id 不变）")
    void shouldBeIdempotentWhenPanelReady() throws Exception {
        String discussionId = createDiscussion();
        String first = generatePanel(discussionId, false, true);
        String second = generatePanel(discussionId, false, false);

        JsonNode firstSeats = seatIdsFrom(first);
        JsonNode secondSeats = seatIdsFrom(second);
        assertThat(secondSeats.size()).isEqualTo(firstSeats.size());
        for (int i = 0; i < firstSeats.size(); i++) {
            assertThat(secondSeats.get(i).get("id").asText())
                    .isEqualTo(firstSeats.get(i).get("id").asText());
        }
    }

    @Test
    @DisplayName("I-4：同一席位第二人认领 → 409 SEAT_TAKEN；撤销后恢复 ACTIVE")
    void shouldRejectSeatTakenAndAllowRevoke() throws Exception {
        String discussionId = createDiscussion();
        generatePanel(discussionId, false, true);
        String seatId = seatIdsFrom(generatePanel(discussionId, false, false)).get(1).get("id").asText();

        mockMvc.perform(post("/api/discussions/" + discussionId + "/claim")
                        .header("X-Device-Id", DEVICE_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("seatId", seatId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PLAYER"))
                .andExpect(jsonPath("$.seatStatus").value("REPLACED_BY_USER"));

        mockMvc.perform(post("/api/discussions/" + discussionId + "/claim")
                        .header("X-Device-Id", DEVICE_B)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("seatId", seatId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SEAT_TAKEN"));

        mockMvc.perform(post("/api/discussions/" + discussionId + "/claim")
                        .header("X-Device-Id", DEVICE_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seatId\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("VIEWER"));

        mockMvc.perform(get("/api/discussions/" + discussionId + "/panel")
                        .header("X-Device-Id", DEVICE_A))
                .andExpect(jsonPath("$.seats[1].status").value("ACTIVE"));
    }

    @Test
    @DisplayName("认领与撤销都不清空席位关注点（focus JSON 列写回可读）")
    void shouldKeepFocusAcrossClaimAndRevoke() throws Exception {
        String discussionId = createDiscussion();
        generatePanel(discussionId, false, true);

        JsonNode seats = readSeats(discussionId);
        String seatId = seats.get(1).get("id").asText();
        JsonNode focus = seats.get(1).get("focus");
        assertThat(focus.isArray()).isTrue();
        assertThat(focus.size()).isGreaterThan(0);

        mockMvc.perform(post("/api/discussions/" + discussionId + "/claim")
                        .header("X-Device-Id", DEVICE_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("seatId", seatId))))
                .andExpect(status().isOk());
        assertThat(readSeats(discussionId).get(1).get("focus")).isEqualTo(focus);

        mockMvc.perform(post("/api/discussions/" + discussionId + "/claim")
                        .header("X-Device-Id", DEVICE_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seatId\":null}"))
                .andExpect(status().isOk());
        assertThat(readSeats(discussionId).get(1).get("focus")).isEqualTo(focus);
    }

    private JsonNode readSeats(String discussionId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/discussions/" + discussionId + "/panel")
                        .header("X-Device-Id", DEVICE_A))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("seats");
    }

    @Test
    @DisplayName("认领主持人席位 → 409 SEAT_NOT_CLAIMABLE；不存在的席位 → 404")
    void shouldRejectHostAndUnknownSeat() throws Exception {
        String discussionId = createDiscussion();
        generatePanel(discussionId, false, true);
        JsonNode seats = seatIdsFrom(generatePanel(discussionId, false, false));

        mockMvc.perform(post("/api/discussions/" + discussionId + "/claim")
                        .header("X-Device-Id", DEVICE_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("seatId", seats.get(0).get("id").asText()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SEAT_NOT_CLAIMABLE"));

        mockMvc.perform(post("/api/discussions/" + discussionId + "/claim")
                        .header("X-Device-Id", DEVICE_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("seatId", "st_not_exists"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SEAT_NOT_FOUND"));
    }

    @Test
    @DisplayName("换一批达到上限后 → 429 REGEN_LIMIT")
    void shouldRejectRegenerateBeyondLimit() throws Exception {
        String discussionId = createDiscussion();
        generatePanel(discussionId, false, true);
        generatePanel(discussionId, true, true);
        generatePanel(discussionId, true, true);

        mockMvc.perform(post("/api/discussions/" + discussionId + "/panel")
                        .header("X-Device-Id", DEVICE_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .content("{\"regenerate\":true,\"preset\":true}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("REGEN_LIMIT"));
    }

    @Test
    @DisplayName("换一批：重新生成后席位 id 变化，未认领席位被替换")
    void shouldRegenerateSeats() throws Exception {
        String discussionId = createDiscussion();
        String first = generatePanel(discussionId, false, true);
        String regenerated = generatePanel(discussionId, true, true);

        assertThat(seatIdsFrom(regenerated).get(0).get("id").asText())
                .isNotEqualTo(seatIdsFrom(first).get(0).get("id").asText());
        assertThat(eventNames(regenerated)).contains("panel.analysis", "panel.done");
    }
}
