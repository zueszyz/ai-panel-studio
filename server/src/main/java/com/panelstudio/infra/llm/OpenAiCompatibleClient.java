package com.panelstudio.infra.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.panelstudio.infra.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * OpenAI 兼容协议的实现（WebClient 阻塞调用）。
 * 密钥只从 AppProperties（环境变量）读取，日志与异常都不带上游原文。
 */
@Component
public class OpenAiCompatibleClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleClient.class);

    private final AppProperties properties;
    private final WebClient webClient;
    private final String chatUri;

    public OpenAiCompatibleClient(AppProperties properties) {
        this.properties = properties;
        if (properties.getLlm().isConfigured()) {
            String baseUrl = stripTrailingSlash(properties.getLlm().getBaseUrl());
            this.chatUri = baseUrl + "/chat/completions";
            this.webClient = WebClient.builder()
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getLlm().getApiKey())
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build();
        } else {
            this.chatUri = null;
            this.webClient = null;
        }
    }

    @Override
    public LlmResult chatJson(String systemPrompt, String userPrompt) {
        if (webClient == null) {
            throw LlmException.notConfigured();
        }
        long startedAt = System.currentTimeMillis();
        Map<String, Object> body = Map.of(
                "model", properties.getLlm().getModel(),
                "temperature", properties.getLlm().getTemperature(),
                "max_tokens", properties.getLlm().getMaxTokens(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)));

        try {
            JsonNode response = webClient.post()
                    .uri(URI.create(chatUri))
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, this::mapStatus)
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofMillis(properties.getTimeout().getTotalMs()))
                    .block();
            if (response == null) {
                throw LlmException.upstream("模型没有返回内容");
            }
            String content = response.path("choices").path(0).path("message").path("content").asText("");
            if (content.isBlank()) {
                throw LlmException.upstream("模型返回内容为空");
            }
            int tokensIn = response.path("usage").path("prompt_tokens").asInt(0);
            int tokensOut = response.path("usage").path("completion_tokens").asInt(0);
            long latency = System.currentTimeMillis() - startedAt;
            log.debug("agent.invoke model={} tokensIn={} tokensOut={} latencyMs={}",
                    properties.getLlm().getModel(), tokensIn, tokensOut, latency);
            return new LlmResult(content, tokensIn, tokensOut, latency);
        } catch (LlmException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            if (isTimeout(ex)) {
                log.warn("模型调用超时（{}ms）", properties.getTimeout().getTotalMs());
                throw LlmException.timeout();
            }
            log.warn("模型调用失败: {}", ex.getClass().getSimpleName());
            throw LlmException.upstream("模型服务暂时不可用");
        }
    }

    private Mono<? extends Throwable> mapStatus(org.springframework.web.reactive.function.client.ClientResponse response) {
        HttpStatusCode status = response.statusCode();
        if (status.value() == 429) {
            return Mono.just(LlmException.rateLimited());
        }
        return Mono.just(LlmException.upstream("模型返回状态 " + status.value()));
    }

    private boolean isTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof TimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static String stripTrailingSlash(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
