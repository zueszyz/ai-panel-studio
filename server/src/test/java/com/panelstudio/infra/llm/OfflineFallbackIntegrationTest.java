package com.panelstudio.infra.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.panelstudio.domain.model.ChatMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 零密钥开箱：Spring 注入的 LlmClient 自动走内置离线模型，不再抛 LLM_NOT_CONFIGURED。 */
@SpringBootTest(properties = "panel.llm.base-url=")
@ActiveProfiles("test")
class OfflineFallbackIntegrationTest {

    @Autowired
    private LlmClient llmClient;

    @Autowired
    private ObjectMapper objectMapper;

    private com.panelstudio.domain.entity.AgentEntity seat() {
        com.panelstudio.domain.entity.AgentEntity seat = new com.panelstudio.domain.entity.AgentEntity();
        seat.setId("st_1");
        seat.setName("陈立");
        seat.setTitle("战略顾问");
        seat.setStance("先算清成本");
        seat.setFocus(List.of("成本"));
        seat.setVoice("先结论");
        return seat;
    }

    @Test
    @DisplayName("未配置 base-url 时，JSON 与流式调用都返回可用内容")
    void shouldFallBackToBuiltinModel() throws Exception {
        String analysis = llmClient.chatJson(PromptTemplates.analysisSystem(),
                PromptTemplates.analysisUser("我们该不该换向量召回", 4)).content();
        assertThat(objectMapper.readTree(analysis).path("disputes").size()).isGreaterThanOrEqualTo(2);

        List<String> deltas = new ArrayList<>();
        LlmResult stream = llmClient.chatStream(List.of(
                ChatMessage.system(PromptTemplates.speechSystem(seat(), "议题A", "目标B", false)),
                ChatMessage.user("【议题】议题A\n")), new StreamListener() {
            @Override
            public void onFirstToken(long latencyMs) {
            }

            @Override
            public void onDelta(String text) {
                deltas.add(text);
            }
        }, new CancelToken());

        assertThat(stream.content()).isNotBlank();
        assertThat(String.join("", deltas)).isEqualTo(stream.content());
    }
}
