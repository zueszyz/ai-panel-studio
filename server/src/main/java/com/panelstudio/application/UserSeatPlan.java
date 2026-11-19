package com.panelstudio.application;

import com.panelstudio.domain.enums.AgentRoleType;
import com.panelstudio.domain.model.SeatDraft;

import java.util.List;
import java.util.Map;

/**
 * 五席位圆桌的固定席位方案：第 0 席主持人，其余为专家。
 *
 * <p>用户自选一个专家席位后，服务端按这里的角色顺序保留该席位，
 * 只让 AI 生成剩下的席位，避免"选的是技术专家、生成出来却变成财务顾问"。</p>
 */
public final class UserSeatPlan {

    /** 专家席位顺序：index 1..N 依次对应这些角色。 */
    public static final List<AgentRoleType> EXPERT_ROLES = List.of(
            AgentRoleType.STRATEGIST,
            AgentRoleType.ANALYST,
            AgentRoleType.TECH,
            AgentRoleType.FINANCE,
            AgentRoleType.RESEARCHER);

    /** 新圆桌默认 1 主持 + 4 专家。 */
    public static final int DEFAULT_PANEL_SIZE = EXPERT_ROLES.size() + 1;

    private static final Map<AgentRoleType, String> TITLES = Map.of(
            AgentRoleType.STRATEGIST, "战略顾问",
            AgentRoleType.ANALYST, "行业分析师",
            AgentRoleType.TECH, "技术专家",
            AgentRoleType.FINANCE, "财务顾问",
            AgentRoleType.RESEARCHER, "用户研究员");

    private static final Map<AgentRoleType, List<String>> FOCUS = Map.of(
            AgentRoleType.STRATEGIST, List.of("投入产出", "机会成本"),
            AgentRoleType.ANALYST, List.of("行业案例", "竞品动向"),
            AgentRoleType.TECH, List.of("工程可行性", "冷启动"),
            AgentRoleType.FINANCE, List.of("现金流", "回本周期"),
            AgentRoleType.RESEARCHER, List.of("用户访谈", "真实需求"));

    private static final Map<AgentRoleType, String> COLORS = Map.of(
            AgentRoleType.STRATEGIST, "amber",
            AgentRoleType.ANALYST, "teal",
            AgentRoleType.TECH, "indigo",
            AgentRoleType.FINANCE, "violet",
            AgentRoleType.RESEARCHER, "rose");

    private UserSeatPlan() {
    }

    /** 用户选择的角色落在第几席（主持人固定为 0）。 */
    public static int orderIndexOf(AgentRoleType role) {
        return EXPERT_ROLES.indexOf(role) + 1;
    }

    public static boolean isExpert(AgentRoleType role) {
        return role != null && role != AgentRoleType.HOST && EXPERT_ROLES.contains(role);
    }

    public static String titleOf(AgentRoleType role) {
        return TITLES.getOrDefault(role, "专家");
    }

    /** 用户席位的档案：不预设立场，由用户现场表达。 */
    public static SeatDraft draft(AgentRoleType role) {
        SeatDraft draft = new SeatDraft();
        draft.setName("你");
        draft.setTitle(titleOf(role));
        draft.setStance("由你现场表达，不受预设立场限制");
        draft.setFocus(FOCUS.getOrDefault(role, List.of("你的判断", "你的追问")));
        draft.setVoice("自由表达");
        draft.setColorToken(COLORS.getOrDefault(role, "emerald"));
        draft.setRoleType(role);
        return draft;
    }

    /** 第 index 席（index ≥ 1）对应的专家角色。 */
    public static AgentRoleType roleAt(int index) {
        if (index <= 0) {
            return AgentRoleType.HOST;
        }
        int slot = Math.min(index - 1, EXPERT_ROLES.size() - 1);
        return EXPERT_ROLES.get(slot);
    }
}
