package com.panelstudio.infra.llm;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JSON 修复器：模型常把 JSON 包在 markdown 围栏里、或多写尾逗号。
 * 只做「安全且确定」的修复，修不好就抛 LLM_JSON_INVALID，由上层降级为预设阵容。
 * 纯函数，可单测。
 */
public final class JsonRepair {

    private static final Pattern FENCE = Pattern.compile("^\\s*```(?:json)?\\s*|\\s*```\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TRAILING_COMMA = Pattern.compile(",\\s*([}\\]])");
    private static final Pattern JSON_START = Pattern.compile("[{\\[]");

    private JsonRepair() {
    }

    /** 返回可能合法的 JSON 文本；调用方仍需用 Jackson 校验。 */
    public static String repair(String raw) {
        if (raw == null) {
            throw LlmException.jsonInvalid();
        }
        String text = FENCE.matcher(raw.trim()).replaceAll("").trim();
        int start = firstJsonChar(text);
        int end = lastJsonChar(text);
        if (start < 0 || end <= start) {
            throw LlmException.jsonInvalid();
        }
        text = text.substring(start, end + 1);
        text = TRAILING_COMMA.matcher(text).replaceAll("$1");
        return text;
    }

    private static int firstJsonChar(String text) {
        Matcher matcher = JSON_START.matcher(text);
        return matcher.find() ? matcher.start() : -1;
    }

    private static int lastJsonChar(String text) {
        for (int i = text.length() - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == '}' || c == ']') {
                return i;
            }
        }
        return -1;
    }
}
