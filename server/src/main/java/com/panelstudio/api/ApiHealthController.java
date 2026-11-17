package com.panelstudio.api;

import com.panelstudio.application.HealthService;
import com.panelstudio.application.MetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 探活与轻量指标（非 Prometheus 格式）。 */
@RestController
@RequestMapping("/api")
@Tag(name = "运维", description = "探活与指标")
public class ApiHealthController {

    private final HealthService healthService;
    private final MetricsService metricsService;

    public ApiHealthController(HealthService healthService, MetricsService metricsService) {
        this.healthService = healthService;
        this.metricsService = metricsService;
    }

    @GetMapping("/health")
    @Operation(summary = "探活：数据库、模型、运行时长")
    public HealthService.HealthSnapshot health() {
        return healthService.snapshot();
    }

    @GetMapping("/metrics")
    @Operation(summary = "轻量指标：SSE 连接数、活跃场次、首 token 延迟、失败率")
    public MetricsService.Snapshot metrics() {
        return metricsService.snapshot();
    }
}
