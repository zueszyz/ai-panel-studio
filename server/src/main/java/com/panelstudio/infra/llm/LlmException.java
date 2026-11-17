package com.panelstudio.infra.llm;

/**
 * 模型层异常：code 与 docs/API.md §4 一致。
 * 消息里不带上游原文，避免把 Key 或内部响应体泄漏到前端。
 */
public class LlmException extends RuntimeException {

    private final String code;
    private final boolean retriable;

    public LlmException(String code, String message, boolean retriable) {
        super(message);
        this.code = code;
        this.retriable = retriable;
    }

    public String code() {
        return code;
    }

    public boolean retriable() {
        return retriable;
    }

    public static LlmException notConfigured() {
        return new LlmException("LLM_NOT_CONFIGURED", "模型未配置，可先使用预设阵容", false);
    }

    public static LlmException timeout() {
        return new LlmException("LLM_TIMEOUT", "模型响应超时", true);
    }

    public static LlmException upstream(String message) {
        return new LlmException("LLM_UPSTREAM_ERROR", message, true);
    }

    public static LlmException rateLimited() {
        return new LlmException("LLM_RATE_LIMIT", "模型限流", true);
    }

    public static LlmException jsonInvalid() {
        return new LlmException("LLM_JSON_INVALID", "模型输出不是合法 JSON", true);
    }

    /** 讨论结束 / 席位离席导致主动取消，不算故障，不该计入失败率。 */
    public static LlmException cancelled() {
        return new LlmException("CANCELLED", "生成已被取消", false);
    }
}
