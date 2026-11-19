package com.panelstudio.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M1 门禁：Flyway 能建出 7 张表，/api/health 与 /api/metrics 可用。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiHealthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Flyway 在临时 SQLite 上建出 7 张业务表")
    void shouldCreateSevenTables() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name IN "
                        + "('users','discussions','agents','messages','minutes','agent_runs','discussion_participants')",
                String.class);
        assertThat(tables).containsExactlyInAnyOrder(
                "users", "discussions", "agents", "messages", "minutes", "agent_runs", "discussion_participants");
    }

    @Test
    @DisplayName("演示数据已写入：1 场圆桌、4 个席位")
    void shouldSeedDemoData() {
        Integer discussions = jdbcTemplate.queryForObject("SELECT count(*) FROM discussions", Integer.class);
        Integer agents = jdbcTemplate.queryForObject("SELECT count(*) FROM agents", Integer.class);
        assertThat(discussions).isGreaterThanOrEqualTo(1);
        assertThat(agents).isGreaterThanOrEqualTo(4);
    }

    @Test
    @DisplayName("GET /api/health 返回 db=up 与运行时长")
    void shouldExposeHealth() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.db").value("up"))
                .andExpect(jsonPath("$.llm").exists())
                .andExpect(jsonPath("$.uptimeSec").isNumber());
    }

    @Test
    @DisplayName("GET /api/metrics 返回指标结构")
    void shouldExposeMetrics() throws Exception {
        mockMvc.perform(get("/api/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sseConnections").isNumber())
                .andExpect(jsonPath("$.activeSessions").isNumber())
                .andExpect(jsonPath("$.firstTokenMs.p50").isNumber())
                .andExpect(jsonPath("$.agentFailureRate").isNumber())
                .andExpect(jsonPath("$.sqliteBusy").isNumber());
    }
}
