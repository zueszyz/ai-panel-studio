package com.panelstudio.infra.config;

import com.panelstudio.infra.logging.MdcTaskDecorator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Agent 线程池。JDK 17 没有虚拟线程，每个流式读取都要占一个平台线程，
 * 所以这里显式限流：core 8 / max 16 / queue 200 / CallerRunsPolicy。
 */
@Configuration
public class AsyncConfig {

    public static final String AGENT_EXECUTOR = "agentExecutor";

    @Bean(name = AGENT_EXECUTOR)
    public ThreadPoolTaskExecutor agentExecutor(AppProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getExecutor().getCore());
        executor.setMaxPoolSize(properties.getExecutor().getMax());
        executor.setQueueCapacity(properties.getExecutor().getQueue());
        executor.setThreadNamePrefix("agent-");
        executor.setTaskDecorator(new MdcTaskDecorator());
        // 队列满时回退到调用线程，宁可慢也不静默丢任务
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        return executor;
    }
}
