package com.panelstudio.application.session;

import com.panelstudio.domain.entity.AgentEntity;
import com.panelstudio.domain.entity.MessageEntity;
import com.panelstudio.domain.model.ChatMessage;
import com.panelstudio.infra.llm.PromptTemplates;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 上下文装配（rules/30 §6、ARCHITECTURE §3.5）：
 * 系统提示（席位人设） + 最近 6 条原文 + 更早发言的规则化摘要 + 用户最新输入（优先）。
 *
 * <p>这里不调模型、不做 IO，纯函数式装配，便于单测。</p>
 */
@Component
public class ContextBuilder {

    /** 原文保留条数：再多就折叠成摘要，避免 10 回合后上下文无限膨胀。 */
    public static final int KEEP_RECENT = 6;
    private static final int MAX_QUOTE_CHARS = 120;

    public record ContextRequest(AgentEntity seat,
                                 String topic,
                                 String goal,
                                 List<MessageEntity> history,
                                 Map<String, String> seatNames,
                                 MessageEntity latestUser,
                                 String quoteContent,
                                 boolean opening,
                                 boolean digest) {
    }

    public List<ChatMessage> build(ContextRequest request) {
        String system = request.opening()
                ? PromptTemplates.openingSystem(request.seat(), request.topic(), request.goal())
                : PromptTemplates.speechSystem(request.seat(), request.topic(), request.goal(), request.digest());

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(system));
        messages.add(ChatMessage.user(historyBlock(request)));
        return messages;
    }

    private String historyBlock(ContextRequest request) {
        StringBuilder block = new StringBuilder();
        block.append("【议题】").append(request.topic()).append('\n');
        if (request.goal() != null && !request.goal().isBlank()) {
            block.append("【本场目标】").append(request.goal()).append('\n');
        }

        List<MessageEntity> history = request.history() == null ? List.of() : request.history();
        int from = Math.max(0, history.size() - KEEP_RECENT);
        if (from > 0) {
            block.append("【更早的发言】已折叠为 ")
                    .append(from)
                    .append(" 条，讨论已进行到第 ")
                    .append(request.history().get(history.size() - 1).getRoundNo())
                    .append(" 回合，此前的分歧已被反复讨论。\n");
        }
        if (from < history.size()) {
            block.append("【最近发言】\n");
        }
        for (int index = from; index < history.size(); index++) {
            MessageEntity message = history.get(index);
            block.append('·')
                    .append(speakerLabel(message, request.seatNames()))
                    .append("（第 ").append(message.getRoundNo()).append(" 回合）：")
                    .append(truncate(message.getContent(), 200))
                    .append('\n');
        }

        if (request.latestUser() != null) {
            block.append("【用户最新发言 · 必须优先回应】")
                    .append(truncate(request.latestUser().getContent(), 400))
                    .append('\n');
        }
        if (request.quoteContent() != null && !request.quoteContent().isBlank()) {
            block.append("【被引用的原话】")
                    .append(truncate(request.quoteContent(), MAX_QUOTE_CHARS))
                    .append('\n');
        }
        block.append("请只输出你要说的这一段话，不要任何前缀或解释。");
        return block.toString();
    }

    private static String speakerLabel(MessageEntity message, Map<String, String> seatNames) {
        if (message.getAgentId() != null) {
            return seatNames.getOrDefault(message.getAgentId(), "某位嘉宾");
        }
        return message.getUserId() != null ? "你" : "主持人";
    }

    private static String truncate(String text, int max) {
        String value = text == null ? "" : text.replace('\n', ' ').trim();
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }
}
