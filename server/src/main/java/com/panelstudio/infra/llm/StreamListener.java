package com.panelstudio.infra.llm;

/** 流式回调：业务层只关心"首 token 到了"和"又来了一段文本"。 */
public interface StreamListener {

    /** 首个 token 到达（用于首 token 延迟指标与前端占位转流式）。 */
    void onFirstToken(long latencyMs);

    /** 增量文本（已解码，调用方负责合并发送）。 */
    void onDelta(String text);
}
