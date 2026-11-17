package com.panelstudio.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.panelstudio.domain.model.SeatDraft;
import com.panelstudio.domain.model.TopicAnalysis;
import com.panelstudio.infra.llm.JsonRepair;
import com.panelstudio.infra.llm.LlmException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 模型输出解析：先修复（围栏 / 尾逗号 / 前后闲话），再严格校验字段。
 * 解析失败抛 LLM_JSON_INVALID；业务字段不达标抛 SeatValidationException（可重问一次）。
 */
@Component
public class PanelParser {

    private static final int MAX_LIST_ITEMS = 5;

    private final ObjectMapper objectMapper;

    public PanelParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public TopicAnalysis parseAnalysis(String raw) {
        JsonNode root = readTree(raw);
        List<String> disputes = texts(root.path("disputes"));
        List<String> angles = texts(root.path("angles"));
        if (disputes.isEmpty() || angles.isEmpty()) {
            throw LlmException.jsonInvalid();
        }
        return new TopicAnalysis(
                root.path("goal").asText("把议题拆成可判断的分歧"),
                limit(disputes),
                limit(angles),
                limit(texts(root.path("audience"))));
    }

    public SeatDraft parseSeat(String raw) {
        JsonNode root = readTree(raw);
        SeatDraft draft = new SeatDraft();
        draft.setName(root.path("name").asText(""));
        draft.setTitle(root.path("title").asText(""));
        draft.setStance(root.path("stance").asText(""));
        draft.setFocus(texts(root.path("focus")));
        draft.setVoice(root.path("voice").asText(""));
        draft.setColorToken(root.path("colorToken").asText("teal"));
        draft.normalize();
        List<String> errors = draft.validate();
        if (!errors.isEmpty()) {
            throw new SeatValidationException(errors);
        }
        return draft;
    }

    private JsonNode readTree(String raw) {
        try {
            return objectMapper.readTree(JsonRepair.repair(raw));
        } catch (LlmException ex) {
            throw ex;
        } catch (Exception ex) {
            throw LlmException.jsonInvalid();
        }
    }

    private List<String> texts(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                String text = item.asText("").trim();
                if (!text.isEmpty()) {
                    values.add(text);
                }
            }
        }
        return values;
    }

    private List<String> limit(List<String> values) {
        return values.size() <= MAX_LIST_ITEMS ? values : values.subList(0, MAX_LIST_ITEMS);
    }
}
