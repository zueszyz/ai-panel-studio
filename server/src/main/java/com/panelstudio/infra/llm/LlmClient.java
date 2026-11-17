package com.panelstudio.infra.llm;

import com.panelstudio.domain.model.ChatMessage;

import java.util.List;

/**
 * 模型适配层：不向业务层泄漏 provider 细节（不 new WebClient、不拼 URL）。
 * 只暴露两个方法：一次性 JSON 与流式文本。
 */
public interface LlmClient {

    /** 一次性 JSON 调用（议题分析、席位生成、纪要结构化）。 */
    LlmResult chatJson(String systemPrompt, String userPrompt);

    /**
     * 流式调用：逐个 token 回调，返回最终完整文本与耗时。
     * 取消由 {@link CancelToken} 表达，实现方负责 dispose 订阅。
     */
    LlmResult chatStream(List<ChatMessage> messages, StreamListener listener, CancelToken token);
}
