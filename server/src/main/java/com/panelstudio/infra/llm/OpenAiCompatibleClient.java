package com.panelstudio.infra.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.panelstudio.domain.model.ChatMessage;
import com.panelstudio.infra.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * OpenAI 兼容协议的实现（WebClient 阻塞调用）。
 * 密钥只从 AppProperties（环境变量）读取，日志与异常都不带上游原文。
 */
@Component
public class OpenAiCompatibleClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleClient.class);
    private static final String DONE = "[DONE]";

    private final AppProperties properties;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;
    private final String chatUri;

    public OpenAiCompatibleClient(AppProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
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
        requireConfigured();
        long startedAt = System.currentTimeMillis();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getLlm().getModel());
        body.put("temperature", properties.getLlm().getTemperature());
        body.put("max_tokens", properties.getLlm().getMaxTokens());
        body.put("messages", List.of(
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
            throw translate(ex, "chatJson");
        }
    }

    @Override
    public LlmResult chatStream(List<ChatMessage> messages, StreamListener listener, CancelToken token) {
        requireConfigured();
        long startedAt = System.currentTimeMillis();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getLlm().getModel());
        body.put("temperature", properties.getLlm().getTemperature());
        body.put("max_tokens", properties.getLlm().getMaxTokens());
        body.put("stream", true);
        body.put("messages", messages.stream()
                .map(message -> Map.of("role", message.role(), "content", message.content()))
                .toList());

        StringBuilder text = new StringBuilder();
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicLong firstTokenAt = new AtomicLong(0);

        Disposable subscription = webClient.post()
                .uri(URI.create(chatUri))
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::mapStatus)
                .bodyToFlux(String.class)
                .timeout(Duration.ofMillis(properties.getTimeout().getFirstTokenMs()))
                .subscribe(chunk -> {
                    if (token.isCancelled()) {
                        return;
                    }
                    String delta = extractDelta(chunk);
                    if (delta == null || delta.isEmpty()) {
                        return;
                    }
                    if (firstTokenAt.compareAndSet(0, System.currentTimeMillis() - startedAt)) {
                        listener.onFirstToken(firstTokenAt.get());
                    }
                    text.append(delta);
                    listener.onDelta(delta);
                }, error -> {
                    failure.set(error);
                    finished.countDown();
                }, finished::countDown);

        token.onCancel(subscription::dispose);

        try {
            boolean completed = finished.await(properties.getTimeout().getTotalMs(), TimeUnit.MILLISECONDS);
            if (!completed) {
                subscription.dispose();
                throw LlmException.timeout();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            subscription.dispose();
            throw LlmException.timeout();
        }

        Throwable error = failure.get();
        if (error != null) {
            throw translate(error, "chatStream");
        }
        if (token.isCancelled()) {
            throw LlmException.cancelled();
        }
        if (text.isEmpty()) {
            throw LlmException.upstream("模型返回内容为空");
        }
        long latency = System.currentTimeMillis() - startedAt;
        int tokensOut = estimateTokens(text.length());
        log.debug("agent.stream model={} chars={} latencyMs={} firstTokenMs={}",
                properties.getLlm().getModel(), text.length(), latency, firstTokenAt.get());
        return new LlmResult(text.toString(), 0, tokensOut, latency);
    }

    /** 解析 OpenAI 流式分片；非内容分片（心跳、usage、[DONE]）返回 null。 */
    private String extractDelta(String chunk) {
        if (chunk == null) {
            return null;
        }
        String payload = chunk.trim();
        if (payload.isEmpty() || DONE.equals(payload)) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(payload);
            JsonNode delta = node.path("choices").path(0).path("delta").path("content");
            return delta.isMissingNode() || delta.isNull() ? null : delta.asText();
        } catch (Exception ex) {
            log.debug("忽略无法解析的流式分片（{} 字符）", payload.length());
            return null;
        }
    }

    private static int estimateTokens(int chars) {
        // 中文接近 1 字 ≈ 1 token，粗略按 2 字 ≈ 1 token 估算，宁少不多
        return Math.max(1, chars / 2);
    }

    private void requireConfigured() {
        if (webClient == null) {
            throw LlmException.notConfigured();
        }
    }

    private LlmException translate(Throwable throwable, String stage) {
        if (throwable instanceof LlmException llm) {
            return llm;
        }
        if (isTimeout(throwable)) {
            log.warn("模型调用超时 stage={} timeoutMs={}", stage, properties.getTimeout().getTotalMs());
            return LlmException.timeout();
        }
        log.warn("模型调用失败 stage={} error={}", stage, throwable.getClass().getSimpleName());
        return LlmException.upstream("模型服务暂时不可用");
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
            if (current instanceof java.util.concurrent.TimeoutException) {
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
