package com.panelstudio.infra.llm;

/** 一次模型调用的结果：内容 + token 用量 + 耗时。 */
public record LlmResult(String content, int tokensIn, int tokensOut, long latencyMs) {
}
