package com.panelstudio.infra.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.panelstudio.domain.entity.AgentEntity;
import com.panelstudio.domain.enums.AgentRoleType;
import com.panelstudio.domain.model.ChatMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 内置离线模型：零密钥时结构合法、文本非空、流程可落库。 */
class OfflineLlmClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OfflineLlmClient client = new OfflineLlmClient(objectMapper);

    private AgentEntity seat() {
        AgentEntity seat = new AgentEntity();
        seat.setId("st_1");
        seat.setName("陈立");
        seat.setTitle("战略顾问");
        seat.setStance("先算清成本再谈先进性");
        seat.setFocus(List.of("投入产出", "组织成本"));
        seat.setVoice("先给结论再给数字");
        seat.setRoleType(AgentRoleType.STRATEGIST);
        return seat;
    }

    @Test
    @DisplayName("议题分析：返回合法 JSON，含分歧/视角/听众")
    void shouldGenerateAnalysisJson() throws Exception {
        String content = client.chatJson(PromptTemplates.analysisSystem(),
                PromptTemplates.analysisUser("我们该不该换向量召回", 4)).content();
        JsonNode json = objectMapper.readTree(content);

        assertThat(json.path("disputes").size()).isGreaterThanOrEqualTo(2);
        assertThat(json.path("angles").size()).isGreaterThanOrEqualTo(3);
        assertThat(json.path("audience").size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("席位生成：第 1 席专家与主持人分别返回合法档案")
    void shouldGenerateSeatJson() throws Exception {
        String expert = client.chatJson(PromptTemplates.seatSystem(),
                PromptTemplates.seatUser("我们该不该换向量召回", 1, 4, false, List.of())).content();
        String host = client.chatJson(PromptTemplates.seatSystem(),
                PromptTemplates.seatUser("我们该不该换向量召回", 0, 4, true, List.of())).content();

        JsonNode expertJson = objectMapper.readTree(expert);
        JsonNode hostJson = objectMapper.readTree(host);
        assertThat(expertJson.path("name").asText()).isNotBlank();
        assertThat(expertJson.path("focus").size()).isBetween(2, 4);
        assertThat(expertJson.path("colorToken").asText()).isNotBlank();
        assertThat(hostJson.path("title").asText()).isEqualTo("圆桌主持人");
    }

    @Test
    @DisplayName("纪要：sourceMessageIds 只取自上下文里真实出现的消息 id")
    void shouldGenerateMinutesWithRealSources() throws Exception {
        String transcript = "[ms_abc123] 第 1 回合 陈立（战略顾问）：先算成本\n"
                + "[ms_def456] 第 1 回合 周敏（行业分析师）：看同业案例\n";
        String user = PromptTemplates.minutesUser("议题A", "目标B", "陈立、周敏", transcript);

        JsonNode minutes = objectMapper.readTree(client.chatJson(PromptTemplates.minutesSystem(), user).content());
        assertThat(minutes.path("claims").size()).isGreaterThan(0);
        assertThat(minutes.path("disputes").size()).isGreaterThan(0);
        JsonNode claim = minutes.path("claims").get(0);
        for (JsonNode source : claim.path("sourceMessageIds")) {
            assertThat(source.asText()).isIn("ms_abc123", "ms_def456");
        }
    }

    @Test
    @DisplayName("流式发言：首帧与非空增量均已回调，内容不超过 180 字")
    void shouldStreamSpeech() {
        List<String> deltas = new ArrayList<>();
        long[] firstToken = {0};
        StreamListener listener = new StreamListener() {
            @Override
            public void onFirstToken(long latencyMs) {
                firstToken[0] = latencyMs;
            }

            @Override
            public void onDelta(String text) {
                deltas.add(text);
            }
        };
        List<ChatMessage> messages = List.of(
                ChatMessage.system(PromptTemplates.speechSystem(seat(), "议题A", "目标B", false)),
                ChatMessage.user("【议题】议题A\n【用户最新发言 · 必须优先回应】成本到底怎么算？\n"));

        LlmResult result = client.chatStream(messages, listener, new CancelToken());

        assertThat(result.content()).isNotBlank();
        assertThat(result.content().length()).isLessThanOrEqualTo(181);
        assertThat(deltas).isNotEmpty();
        assertThat(String.join("", deltas)).isEqualTo(result.content());
        assertThat(firstToken[0]).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("流式收束：返回非空文本且尊重取消")
    void shouldStreamDigestAndHonorCancel() {
        List<ChatMessage> messages = List.of(
                ChatMessage.system(PromptTemplates.speechSystem(seat(), "议题A", "目标B", true)),
                ChatMessage.user("【议题】议题A\n"));
        assertThat(client.chatStream(messages, new StreamListener() {
            @Override
            public void onFirstToken(long latencyMs) {
            }

            @Override
            public void onDelta(String text) {
            }
        }, new CancelToken()).content()).contains("分歧点");

        CancelToken cancelled = new CancelToken();
        cancelled.cancel();
        assertThatThrownBy(() -> client.chatStream(messages, new StreamListener() {
            @Override
            public void onFirstToken(long latencyMs) {
            }

            @Override
            public void onDelta(String text) {
            }
        }, cancelled)).isInstanceOf(LlmException.class);
    }
}
