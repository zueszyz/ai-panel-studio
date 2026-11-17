package com.panelstudio.infra.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonRepairTest {

    @Test
    @DisplayName("去掉 markdown 围栏")
    void shouldStripFence() {
        String raw = "```json\n{\"name\":\"陈立\"}\n```";
        assertThat(JsonRepair.repair(raw)).isEqualTo("{\"name\":\"陈立\"}");
    }

    @Test
    @DisplayName("去掉尾逗号（数组与对象）")
    void shouldRemoveTrailingComma() {
        String raw = "{\"a\":[1,2,],\"b\":1,}";
        assertThat(JsonRepair.repair(raw)).isEqualTo("{\"a\":[1,2],\"b\":1}");
    }

    @Test
    @DisplayName("从解释性文字里捞出 JSON")
    void shouldExtractJsonFromProse() {
        String raw = "好的，下面是结果：\n{\"ok\":true}\n希望对你有帮助";
        assertThat(JsonRepair.repair(raw)).isEqualTo("{\"ok\":true}");
    }

    @Test
    @DisplayName("完全没有 JSON 时抛 LLM_JSON_INVALID")
    void shouldFailOnGarbage() {
        assertThatThrownBy(() -> JsonRepair.repair("抱歉，我无法回答"))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("JSON");
    }
}
