package com.panelstudio.infra.llm;

/**
 * 模型适配层：不向业务层泄漏 provider 细节（不 new WebClient、不拼 URL）。
 * M2 只需要一次性 JSON 调用；流式调用在讨论内核（M3）补齐。
 */
public interface LlmClient {

    LlmResult chatJson(String systemPrompt, String userPrompt);
}
