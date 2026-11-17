package com.panelstudio.domain.policy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 发言者选择策略：纯函数，无 IO、无状态，可单测。
 *
 * <pre>
 * score(seat) =
 *      3 × 被用户点名 / 文本命中姓名
 *    + 2 × 用户消息命中其 focus 关键词
 *    + 2 × 被引用且尚未回应
 *    + 1 × 距上次发言越久越优先（封顶 5）
 *    - 5 × 本回合已发言
 * </pre>
 *
 * 主持人永不参与选举，只由调度器在开场 / 收束时触发。
 */
public final class SpeakerPolicy {

    private static final int NAME_HIT = 3;
    private static final int FOCUS_HIT = 2;
    private static final int QUOTED = 2;
    private static final int SPOKEN_THIS_TURN = 5;
    private static final int DECAY_CAP = 5;

    private SpeakerPolicy() {
    }

    /** 参与打分的席位快照（不含主持人）。 */
    public record Candidate(String seatId,
                            String name,
                            List<String> focus,
                            int lastSpokeRound,
                            boolean spokenThisTurn) {
    }

    /** 选择结果，reason 用于轨迹面板展示"为什么由他发言"。 */
    public record Decision(String seatId, int score, String reason) {
    }

    /**
     * @param seats        全部候选（含主持人，内部过滤）
     * @param userText     用户最新发言（可为空）
     * @param quotedSeatId 用户引用的席位（可为空）
     * @param round        当前回合号（从 1 开始）
     * @param limit        本回合最多发言人数
     */
    public static List<Decision> pick(List<Candidate> seats, String userText, String quotedSeatId,
                                      int round, int limit) {
        List<Decision> scored = new ArrayList<>();
        String text = userText == null ? "" : userText.toLowerCase(Locale.ROOT);
        for (Candidate seat : seats) {
            if (seat.spokenThisTurn()) {
                continue;
            }
            int score = 0;
            String reason = null;
            if (!seat.name().isBlank() && text.contains(seat.name().toLowerCase(Locale.ROOT))) {
                score += NAME_HIT;
                reason = "被点名";
            }
            if (hitFocus(text, seat.focus())) {
                score += FOCUS_HIT;
                reason = reason == null ? "命中关注点" : reason + "、命中关注点";
            }
            if (quotedSeatId != null && quotedSeatId.equals(seat.seatId())) {
                score += QUOTED;
                reason = reason == null ? "回应引用" : reason + "、回应引用";
            }
            int gap = Math.max(0, Math.min(round - seat.lastSpokeRound(), DECAY_CAP));
            if (gap > 0) {
                score += gap;
                if (reason == null) {
                    reason = gap >= DECAY_CAP ? "久未发言" : "间隔 " + gap + " 回合";
                }
            }
            if (score == 0) {
                reason = "主持人指派";
            }
            scored.add(new Decision(seat.seatId(), score, reason));
        }

        scored.sort(Comparator.comparingInt(Decision::score).reversed()
                .thenComparing(Decision::seatId));
        if (scored.size() <= limit) {
            return List.copyOf(scored);
        }
        return List.copyOf(scored.subList(0, Math.max(1, limit)));
    }

    private static boolean hitFocus(String text, List<String> focus) {
        if (focus == null || text.isEmpty()) {
            return false;
        }
        for (String keyword : focus) {
            if (keyword != null && !keyword.isBlank() && text.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /** 本回合已发言的席位不再入选（规则里的 -5 直接表达为跳过，避免重复发言）。 */
    public static int penaltyForRepeat() {
        return SPOKEN_THIS_TURN;
    }
}
