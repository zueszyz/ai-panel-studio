package com.panelstudio.infra.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger UI（/swagger-ui.html）与 docs/API.md 双向对齐的最小配置。 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI panelStudioOpenApi() {
        return new OpenAPI().info(new Info()
                .title("AI 圆桌演播厅 API")
                .version("v1")
                .description("议题 → 组局 → 讨论 → 纪要。错误体统一为 {\"error\":{\"code\",\"message\",\"details\"}}。"));
    }
}
