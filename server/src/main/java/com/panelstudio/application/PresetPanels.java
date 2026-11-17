package com.panelstudio.application;

import com.panelstudio.domain.enums.AgentRoleType;
import com.panelstudio.domain.model.SeatDraft;
import com.panelstudio.domain.model.TopicAnalysis;

import java.util.ArrayList;
import java.util.List;

/**
 * 预设阵容兜底：模型不可用（未配置 / 连续失败 / 脏输出）时，仍能离线组一场像样的圆桌。
 * 3 套模板，按议题哈希选一套，保证「换一批」能看到不同阵容。
 */
public final class PresetPanels {

    private PresetPanels() {
    }

    public static TopicAnalysis analysis(String topic) {
        return new TopicAnalysis(
                "把「" + topic + "」拆成可判断的分歧，并给出下一步",
                List.of("现在做与半年后做的差别", "成本与收益是否算得清", "失败时谁来兜底"),
                List.of("成本", "用户价值", "组织落地", "技术可行性"),
                List.of("决策者", "执行团队"));
    }

    /** 返回恰好 panelSize 个席位，第 0 席固定为主持人。 */
    public static List<SeatDraft> seats(String topic, int panelSize) {
        List<Variant> variants = List.of(costVariant(), userVariant(), competitionVariant());
        Variant variant = variants.get(Math.floorMod(topic == null ? 0 : topic.hashCode(), variants.size()));
        List<SeatDraft> seats = new ArrayList<>();
        seats.add(host(variant.hostName, variant.hostVoice));
        for (int i = 0; i < panelSize - 1 && i < variant.experts.size(); i++) {
            seats.add(variant.experts.get(i));
        }
        return seats;
    }

    private record Variant(String hostName, String hostVoice, List<SeatDraft> experts) {
    }

    private static Variant costVariant() {
        return new Variant("沈知远", "克制、善用短句", List.of(
                expert("陈立", "战略顾问", "先算清 ROI，再谈先进性", List.of("投入产出", "组织成本"),
                        "先给结论再给数字", "amber", AgentRoleType.STRATEGIST),
                expert("周敏", "行业分析师", "别只看自家数据，先看同业曲线", List.of("行业案例", "竞品动向"),
                        "用案例说话", "teal", AgentRoleType.ANALYST),
                expert("林恒", "技术专家", "工程可行性和数据基础先过关", List.of("工程可行性", "冷启动"),
                        "举例类比，直给坑位", "indigo", AgentRoleType.TECH),
                expert("何宁", "财务顾问", "现金流撑不住的方案都不是好方案", List.of("TCO", "回款周期"),
                        "算账式表达", "violet", AgentRoleType.FINANCE),
                expert("苏芮", "用户研究员", "用户不会为团队的自我感动买单", List.of("用户访谈", "真实需求"),
                        "先讲一个用户故事", "rose", AgentRoleType.RESEARCHER)));
    }

    private static Variant userVariant() {
        return new Variant("许清", "克制、短句、会复述对方观点", List.of(
                expert("苏芮", "用户研究员", "先用十个用户访谈把关，再谈方案", List.of("用户访谈", "真实需求"),
                        "先讲一个用户故事", "rose", AgentRoleType.RESEARCHER),
                expert("郑楠", "组织顾问", "没有明确负责人，方案一定烂尾", List.of("权责划分", "推进节奏"),
                        "只谈谁负责什么", "violet", AgentRoleType.STRATEGIST),
                expert("高远", "落地负责人", "三个月跑不出第一版就别做", List.of("交付节奏", "试点范围"),
                        "直给时间表", "indigo", AgentRoleType.TECH),
                expert("李澜", "行业分析师", "同行试过，结果比预想的惨", List.of("同业案例", "失败复盘"),
                        "用案例说话", "teal", AgentRoleType.ANALYST),
                expert("陈立", "战略顾问", "先算清 ROI，再谈先进性", List.of("投入产出", "机会成本"),
                        "先给结论再给数字", "amber", AgentRoleType.STRATEGIST)));
    }

    private static Variant competitionVariant() {
        return new Variant("沈知远", "克制、善用短句", List.of(
                expert("罗成", "竞争分析师", "对手的弱点不一定是你的机会", List.of("竞争格局", "差异化"),
                        "先摆一张对比表", "amber", AgentRoleType.ANALYST),
                expert("方玥", "合规顾问", "先看红线，再谈创新", List.of("合规风险", "数据边界"),
                        "条款式表达", "violet", AgentRoleType.FINANCE),
                expert("林恒", "技术专家", "工程可行性和数据基础先过关", List.of("技术债", "可维护性"),
                        "举例类比，直给坑位", "indigo", AgentRoleType.TECH),
                expert("何宁", "财务顾问", "现金流撑不住的方案都不是好方案", List.of("现金流", "折旧"),
                        "算账式表达", "teal", AgentRoleType.FINANCE),
                expert("周敏", "行业分析师", "别只看自家数据，先看同业曲线", List.of("行业案例", "对标口径"),
                        "用案例说话", "rose", AgentRoleType.ANALYST)));
    }

    private static SeatDraft host(String name, String voice) {
        SeatDraft draft = new SeatDraft();
        draft.setName(name);
        draft.setTitle("圆桌主持人");
        draft.setStance("让分歧被看见，而不是被抹平");
        draft.setFocus(List.of("议题拆解", "串联", "收束"));
        draft.setVoice(voice);
        draft.setColorToken("slate");
        draft.setRoleType(AgentRoleType.HOST);
        return draft;
    }

    private static SeatDraft expert(String name, String title, String stance, List<String> focus,
                                    String voice, String colorToken, AgentRoleType roleType) {
        SeatDraft draft = new SeatDraft();
        draft.setName(name);
        draft.setTitle(title);
        draft.setStance(stance);
        draft.setFocus(focus);
        draft.setVoice(voice);
        draft.setColorToken(colorToken);
        draft.setRoleType(roleType);
        return draft;
    }
}
