package com.panelstudio.application.session;

import com.panelstudio.domain.entity.AgentEntity;
import com.panelstudio.domain.entity.MessageEntity;
import com.panelstudio.domain.model.ChatMessage;
import com.panelstudio.infra.llm.PromptTemplates;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    /** 折叠摘要上限（ARCHITECTURE §3.5：≤400 字），不把旧原文逐字塞回上下文。 */
    private static final int MAX_SUMMARY_CHARS = 400;

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
            block.append(foldSummary(history, from, request.seatNames())).append('\n');
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

    /**
     * 规则化滚动摘要：只保留「谁在哪些回合说了几次」，不把旧原文逐字带回上下文。
     * 纯函数、不调模型；每 4 回合折叠一次的历史在这里被压成一行，长度封顶 400 字。
     */
    private static String foldSummary(List<MessageEntity> history, int from, Map<String, String> seatNames) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        int minRound = Integer.MAX_VALUE;
        int maxRound = 0;
        for (int index = 0; index < from; index++) {
            MessageEntity message = history.get(index);
            counts.merge(speakerLabel(message, seatNames), 1, Integer::sum);
            int round = message.getRoundNo() == null ? 0 : message.getRoundNo();
            minRound = Math.min(minRound, round);
            maxRound = Math.max(maxRound, round);
        }
        StringBuilder summary = new StringBuilder("【更早的发言】已折叠为 ")
                .append(from).append(" 条（第 ")
                .append(minRound == Integer.MAX_VALUE ? 0 : minRound)
                .append("–").append(maxRound).append(" 回合）：");
        counts.forEach((name, count) -> summary.append(name).append('×').append(count).append(' '));
        String text = summary.toString().trim();
        return text.length() <= MAX_SUMMARY_CHARS
                ? text
                : text.substring(0, MAX_SUMMARY_CHARS) + "…";
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
