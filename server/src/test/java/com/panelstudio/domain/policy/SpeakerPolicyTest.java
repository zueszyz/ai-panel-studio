package com.panelstudio.domain.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpeakerPolicyTest {

    private SpeakerPolicy.Candidate seat(String id, String name, List<String> focus, int lastRound) {
        return new SpeakerPolicy.Candidate(id, name, focus, lastRound, false);
    }

    @Test
    @DisplayName("被点名的席位优先入选")
    void shouldPreferMentionedSeat() {
        List<SpeakerPolicy.Decision> picked = SpeakerPolicy.pick(List.of(
                seat("st_1", "陈立", List.of("成本"), 1),
                seat("st_2", "周敏", List.of("行业"), 1),
                seat("st_3", "林恒", List.of("工程"), 1)), "陈立你怎么看这件事", null, 2, 2);

        assertThat(picked).isNotEmpty();
        assertThat(picked.get(0).seatId()).isEqualTo("st_1");
        assertThat(picked.get(0).reason()).contains("被点名");
    }

    @Test
    @DisplayName("命中关注点加分，且与点名可叠加")
    void shouldScoreFocusHit() {
        List<SpeakerPolicy.Decision> picked = SpeakerPolicy.pick(List.of(
                seat("st_1", "陈立", List.of("成本", "组织"), 1),
                seat("st_2", "周敏", List.of("行业案例"), 1)), "迁移成本到底谁来承担", null, 2, 1);

        assertThat(picked).hasSize(1);
        assertThat(picked.get(0).seatId()).isEqualTo("st_1");
        assertThat(picked.get(0).reason()).contains("命中关注点");
    }

    @Test
    @DisplayName("被引用的席位必须进入本轮")
    void shouldIncludeQuotedSeat() {
        List<SpeakerPolicy.Decision> picked = SpeakerPolicy.pick(List.of(
                seat("st_1", "陈立", List.of("成本"), 2),
                seat("st_2", "周敏", List.of("行业案例"), 2),
                seat("st_3", "林恒", List.of("工程"), 2)), "接着刚才说的", "st_3", 3, 2);

        assertThat(picked.stream().map(SpeakerPolicy.Decision::seatId)).contains("st_3");
    }

    @Test
    @DisplayName("久未发言者优先，本回合已发言者不再入选")
    void shouldUseDecayAndSkipSpoken() {
        List<SpeakerPolicy.Decision> picked = SpeakerPolicy.pick(List.of(
                new SpeakerPolicy.Candidate("st_1", "陈立", List.of(), 3, true),
                new SpeakerPolicy.Candidate("st_2", "周敏", List.of(), 0, false),
                new SpeakerPolicy.Candidate("st_3", "林恒", List.of(), 3, false)), "", null, 4, 2);

        assertThat(picked.stream().map(SpeakerPolicy.Decision::seatId)).doesNotContain("st_1");
        assertThat(picked.get(0).seatId()).isEqualTo("st_2");
        assertThat(picked.get(0).reason()).containsAnyOf("久未发言", "间隔");
    }

    @Test
    @DisplayName("点名 + 关注点 + 引用可叠加，综合分最高者排首位")
    void shouldStackSignals() {
        List<SpeakerPolicy.Decision> picked = SpeakerPolicy.pick(List.of(
                seat("st_1", "陈立", List.of("成本"), 2),
                seat("st_2", "周敏", List.of("成本"), 2)), "陈立，成本到底谁来承担", "st_1", 3, 1);

        assertThat(picked).hasSize(1);
        assertThat(picked.get(0).seatId()).isEqualTo("st_1");
        assertThat(picked.get(0).reason()).contains("被点名").contains("命中关注点");
        assertThat(picked.get(0).score()).isGreaterThan(5);
    }

    @Test
    @DisplayName("K 上限生效，空候选返回空")
    void shouldRespectLimitAndEmpty() {
        List<SpeakerPolicy.Decision> picked = SpeakerPolicy.pick(List.of(
                seat("st_1", "陈立", List.of(), 0),
                seat("st_2", "周敏", List.of(), 0),
                seat("st_3", "林恒", List.of(), 0),
                seat("st_4", "何宁", List.of(), 0)), "", null, 1, 2);

        assertThat(picked).hasSize(2);
        assertThat(SpeakerPolicy.pick(List.of(), "你好", null, 1, 2)).isEmpty();
    }
}
