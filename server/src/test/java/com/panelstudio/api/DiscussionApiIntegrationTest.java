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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 大厅契约：I-1（参数校验）+ 列表/筛选/搜索/分页 + 详情 404。
 * 使用临时 SQLite，Flyway 在启动时建表并写入 V3/V4 种子数据。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DiscussionApiIntegrationTest {

    private static final String DEVICE_ID = "test-device-0001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("I-1：7 字议题 → 400 VALIDATION_ERROR，details[0].field=topic")
    void shouldRejectShortTopic() throws Exception {
        mockMvc.perform(post("/api/discussions")
                        .header("X-Device-Id", DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topic\":\"七个字的议题\",\"panelSize\":4}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value("topic"));
    }

    @Test
    @DisplayName("参会人数越界 → 400 VALIDATION_ERROR，details 指向 panelSize")
    void shouldRejectInvalidPanelSize() throws Exception {
        mockMvc.perform(post("/api/discussions")
                        .header("X-Device-Id", DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topic\":\"我们该不该把推荐系统换成向量召回\",\"panelSize\":9}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value("panelSize"));
    }

    @Test
    @DisplayName("创建圆桌 → 201，状态 DRAFT，主键以 dk_ 开头")
    void shouldCreateDiscussion() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/discussions")
                        .header("X-Device-Id", DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "topic", "我们该不该把推荐系统从规则引擎换成向量召回",
                                "panelSize", 4))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.panelSize").value(4))
                .andExpect(jsonPath("$.turnIndex").value(0))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("id").asText()).startsWith("dk_");
    }

    @Test
    @DisplayName("列表：返回种子数据，LIVE 排在最前")
    void shouldListDiscussionsWithLiveFirst() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/discussions")
                        .header("X-Device-Id", DEVICE_ID)
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andReturn();

        JsonNode items = objectMapper.readTree(result.getResponse().getContentAsString()).get("items");
        assertThat(items.size()).isGreaterThanOrEqualTo(5);
        assertThat(items.get(0).get("status").asText()).isEqualTo("LIVE");
        assertThat(items.get(0).get("seats").size()).isEqualTo(4);
    }

    @Test
    @DisplayName("列表：status 与关键词筛选生效")
    void shouldFilterByStatusAndKeyword() throws Exception {
        mockMvc.perform(get("/api/discussions")
                        .header("X-Device-Id", DEVICE_ID)
                        .param("status", "CLOSED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.status != 'CLOSED')]").isEmpty());

        mockMvc.perform(get("/api/discussions")
                        .header("X-Device-Id", DEVICE_ID)
                        .param("q", "客服"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].topic").value(org.hamcrest.Matchers.containsString("客服")));
    }

    @Test
    @DisplayName("列表：mine=true 只返回我参与的（种子用户 demo-device-0001）")
    void shouldFilterMine() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/discussions")
                        .header("X-Device-Id", "demo-device-0001")
                        .param("mine", "true"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode items = objectMapper.readTree(result.getResponse().getContentAsString()).get("items");
        assertThat(items.size()).isGreaterThanOrEqualTo(1);
        for (JsonNode item : items) {
            assertThat(item.get("id").asText()).isIn(
                    "dk_01H8ZQ7M0000000000000000B1",
                    "dk_01H8ZR0000000000000000B2",
                    "dk_01H8ZR0000000000000000B3",
                    "dk_01H8ZR0000000000000000B4",
                    "dk_01H8ZR0000000000000000B5");
        }
    }

    @Test
    @DisplayName("列表：非法状态取值 → 400 VALIDATION_ERROR")
    void shouldRejectUnknownStatus() throws Exception {
        mockMvc.perform(get("/api/discussions").param("status", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details.field").value("status"));
    }

    @Test
    @DisplayName("详情：返回席位与认领标记；不存在 → 404 DISCUSSION_NOT_FOUND")
    void shouldReturnDetailAndNotFound() throws Exception {
        mockMvc.perform(get("/api/discussions/dk_01H8ZR0000000000000000B4")
                        .header("X-Device-Id", "demo-device-0001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LIVE"))
                .andExpect(jsonPath("$.seats.length()").value(4))
                .andExpect(jsonPath("$.seats[0].roleType").value("HOST"))
                .andExpect(jsonPath("$.mySeatId").value("st_01H8ZR0000000000000000D0"));

        mockMvc.perform(get("/api/discussions/dk_not_exists"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("DISCUSSION_NOT_FOUND"));
    }

    @Test
    @DisplayName("软删除：仅创建者可删；列表移除、直链 410、重复删除 410")
    void shouldSoftDeleteOnlyAsCreator() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/discussions")
                        .header("X-Device-Id", DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "topic", "我们该不该把推荐系统从规则引擎换成向量召回",
                                "panelSize", 4))))
                .andExpect(status().isCreated())
                .andReturn();
        String id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(delete("/api/discussions/" + id).header("X-Device-Id", "another-device"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mockMvc.perform(delete("/api/discussions/" + id))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        mockMvc.perform(delete("/api/discussions/" + id).header("X-Device-Id", DEVICE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.deleted").value(true));

        mockMvc.perform(get("/api/discussions/" + id))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error.code").value("DISCUSSION_DELETED"));
        mockMvc.perform(delete("/api/discussions/" + id).header("X-Device-Id", DEVICE_ID))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error.code").value("DISCUSSION_DELETED"));
        mockMvc.perform(get("/api/discussions").param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '" + id + "')]").isEmpty());
    }
}
