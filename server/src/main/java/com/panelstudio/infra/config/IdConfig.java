package com.panelstudio.infra.config;

import com.panelstudio.domain.support.UlidGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 全局唯一的 ULID 生成器：单例保证同毫秒内单调递增，
 * 这样 ORDER BY id 与时间序一致（游标分页依赖这一点）。
 */
@Configuration
public class IdConfig {

    @Bean
    public UlidGenerator ulidGenerator() {
        return new UlidGenerator();
    }
}
