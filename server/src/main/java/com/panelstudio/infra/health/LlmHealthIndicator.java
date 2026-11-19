package com.panelstudio.infra.health;

import com.panelstudio.infra.config.AppProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Actuator 的模型探活：只报告「是否已配置」，不发起真实请求。
 * 真实连通性由 /api/health 之外的降级路径体现（生成失败即走预设阵容）。
 */
@Component
public class LlmHealthIndicator implements HealthIndicator {

    private final AppProperties properties;

    public LlmHealthIndicator(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public Health health() {
        boolean configured = properties.getLlm().isConfigured();
        return Health.up()
                .withDetail("mode", configured ? "remote" : "local")
                .withDetail("configured", configured)
                .withDetail("model", configured ? properties.getLlm().getModel() : "offline-builtin")
                .build();
    }
}
