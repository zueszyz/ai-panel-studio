package com.panelstudio.application;

import com.panelstudio.infra.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;

/**
 * 探活：数据库连通性 + 模型是否已配置（不发起真实模型请求，避免探活变成慢接口）。
 */
@Service
public class HealthService {

    private static final Logger log = LoggerFactory.getLogger(HealthService.class);

    private final JdbcTemplate jdbcTemplate;
    private final AppProperties properties;

    public HealthService(JdbcTemplate jdbcTemplate, AppProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    public HealthSnapshot snapshot() {
        String db = "up";
        try {
            Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            if (one == null || one != 1) {
                db = "down";
            }
        } catch (RuntimeException ex) {
            log.warn("数据库探活失败: {}", ex.getMessage());
            db = "down";
        }
        String llm = properties.getLlm().isConfigured() ? "up" : "down";
        String status = "up".equals(db) ? "ok" : "degraded";
        return new HealthSnapshot(status, db, llm, ManagementFactory.getRuntimeMXBean().getUptime() / 1000);
    }

    public record HealthSnapshot(String status, String db, String llm, long uptimeSec) {
    }
}
