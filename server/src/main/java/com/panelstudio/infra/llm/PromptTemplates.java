package com.panelstudio.infra.llm;

import com.panelstudio.domain.model.SeatDraft;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Prompt 集中管理：业务层不许拼 prompt 字符串（rules/10 §1）。
 * 所有指令都要求「只输出 JSON」，解析失败由 JsonRepair + 一次重问兜底。
 */
public final class PromptTemplates {

    private PromptTemplates() {
    }

    public static String analysisSystem() {
        return """
                你是圆桌会议的策划。你的任务是把一个问题拆成「值得争论的分歧」。
                只输出 JSON，不要任何解释、不要 markdown 代码围栏。
                JSON 结构：{"disputes":["分歧1","分歧2"],"angles":["视角1","视角2"],"audience":["听众1"]}
                disputes 2 到 4 条，angles 3 到 4 条，每条不超过 20 个字，必须是中文。
                """;
    }

    public static String analysisUser(String topic, int panelSize) {
        return "议题：" + topic + "\n本场共 " + panelSize + " 人（1 位主持人 + " + (panelSize - 1) + " 位专家）。"
                + "\n请分析这场讨论真正的争议焦点与需要覆盖的视角。";
    }

    public static String seatSystem() {
        return """
                你是圆桌会议的选角导演。你要为一场关于特定议题的讨论设计一位嘉宾的「人设」。
                要求：
                1. 立场必须锋利且与其他席位构成张力，禁止中立话术；
                2. 姓名像真实的中文名（2 到 3 个字），不要用「专家A」这类占位；
                3. 关注点 2 到 4 个，每个不超过 8 个字；
                4. 说话风格一句话，不超过 20 个字；
                5. colorToken 从 amber / teal / indigo / violet / rose 中选一个，不同席位尽量不同。
                只输出 JSON，不要解释、不要 markdown 代码围栏。结构：
                {"name":"陈立","title":"战略顾问","stance":"一句话立场","focus":["关注点1","关注点2"],"voice":"先给结论再给数字","colorToken":"amber"}
                """;
    }

    public static String seatUser(String topic, int seatIndex, int panelSize, boolean hostSeat,
                                  List<SeatDraft> previousSeats) {
        String previous = previousSeats.isEmpty()
                ? "（这是第一位，先确定主持人的立场与主持风格）"
                : previousSeats.stream()
                .map(seat -> "· " + seat.getName() + "（" + seat.getTitle() + "）：" + seat.getStance())
                .collect(Collectors.joining("\n"));
        return """
                议题：%s
                本场共 %d 席，现在要生成第 %d 席。
                %s
                已有席位（不要重复他们的立场，要与他们形成互补或对立）：
                %s
                标记：SEAT_INDEX:%d
                """.formatted(topic, panelSize, seatIndex + 1,
                hostSeat ? "这一席是主持人：不站队，负责让分歧被看见；title 固定为「圆桌主持人」。"
                        : "这一席是专家：必须有明确立场。",
                previous, seatIndex);
    }

    public static String seatRepairUser(String topic, int seatIndex, String errors) {
        return """
                上一次输出不符合要求：%s
                议题：%s，第 %d 席。请只输出修正后的 JSON，不要任何解释。
                """.formatted(errors, topic, seatIndex + 1);
    }
}
