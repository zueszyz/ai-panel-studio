package com.panelstudio.application.session;

import com.panelstudio.domain.entity.AgentEntity;
import com.panelstudio.domain.entity.MessageEntity;
import com.panelstudio.domain.enums.AgentRoleType;
import com.panelstudio.domain.model.ChatMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ContextBuilderTest {

    private final ContextBuilder builder = new ContextBuilder();

    private AgentEntity seat() {
        AgentEntity seat = new AgentEntity();
        seat.setId("st_1");
        seat.setName("陈立");
        seat.setTitle("战略顾问");
        seat.setStance("先算清 ROI");
        seat.setFocus(List.of("成本", "组织"));
        seat.setVoice("先结论后数字");
        seat.setRoleType(AgentRoleType.STRATEGIST);
        return seat;
    }

    private List<MessageEntity> history(int count) {
        List<MessageEntity> list = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            MessageEntity message = new MessageEntity();
            message.setId("ms_" + index);
            message.setAgentId("st_2");
            message.setRoundNo(index / 2 + 1);
            message.setContent("第 " + index + " 条发言内容");
            list.add(message);
        }
        return list;
    }

    private List<ChatMessage> build(List<MessageEntity> history, MessageEntity user, String quote) {
        return builder.build(new ContextBuilder.ContextRequest(seat(), "要不要换向量召回",
                "判断该不该迁移", history, Map.of("st_2", "周敏"), user, quote, false, false));
    }

    @Test
    @DisplayName("只保留最近 6 条原文，其余折叠为一句摘要")
    void shouldFoldOldMessages() {
        List<ChatMessage> messages = build(history(12), null, null);
        String user = messages.get(1).content();

        assertThat(user).contains("已折叠为 6 条");
        assertThat(user).contains("第 6 条发言内容");
        assertThat(user).doesNotContain("第 5 条发言内容");
    }

    @Test
    @DisplayName("用户最新发言被显式标记为优先")
    void shouldMarkLatestUserMessage() {
        MessageEntity user = new MessageEntity();
        user.setId("ms_u");
        user.setContent("冷启动没有数据，向量召回拿什么训练？");

        List<ChatMessage> messages = build(history(3), user, null);

        assertThat(messages.get(0).role()).isEqualTo("system");
        assertThat(messages.get(0).content()).contains("陈立").contains("先算清 ROI");
        assertThat(messages.get(1).content()).contains("必须优先回应")
                .contains("冷启动没有数据");
    }

    @Test
    @DisplayName("超长引用被截断，且携带原话")
    void shouldTruncateQuote() {
        String quote = "很长的原话".repeat(60);
        List<ChatMessage> messages = build(history(1), null, quote);

        assertThat(messages.get(1).content()).contains("被引用的原话").endsWith("请只输出你要说的这一段话，不要任何前缀或解释。");
        assertThat(messages.get(1).content().length()).isLessThan(quote.length());
    }
}
