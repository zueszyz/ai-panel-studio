package com.panelstudio.infra.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.panelstudio.domain.model.ChatMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 内置离线模型（零密钥兜底）：未配置外部 OpenAI 兼容模型时由 {@link OpenAiCompatibleClient} 委托到这里。
 *
 * <p>用规则从 Prompt 中提取议题、席位、立场与真实消息 id，生成结构合法的中文 JSON 与流式发言，
 * 让组局 → 讨论 → 纪要整条链路在没有 Key 的机器上也能完整演示并落库。
 * 输出是"演示级"内容，不伪装成真实模型结论；配置了模型后自动切回真实调用。</p>
 */
@Component
public class OfflineLlmClient {

    private static final Pattern NAME = Pattern.compile("你叫([^，,]+)");
    private static final Pattern SEAT_INDEX = Pattern.compile("SEAT_INDEX:(\\d+)");
    private static final Pattern MESSAGE_ID = Pattern.compile("ms_[A-Za-z0-9]+");

    /** 席位档案：与 E2E 假模型一致，保证不同席位立场互补。 */
    private static final String[][] HOST = {{
            "沈知远", "圆桌主持人", "不站队，只让分歧被看见", "议题拆解、节奏控制", "克制，只问关键问题", "slate"
    }};
    private static final String[][] EXPERTS = {
            {"陈立", "战略顾问", "先算清成本再谈先进性", "投入产出、组织成本", "先给结论再给数字", "amber"},
            {"周敏", "行业分析师", "同业案例比推演更可靠", "同业案例、竞品动向", "用案例说话", "teal"},
            {"林恒", "技术专家", "冷启动没有数据就没有召回", "冷启动、工程可行性", "直给坑位", "indigo"},
            {"顾南", "组织顾问", "先看谁在这件事里被改变", "组织阻力、协作成本", "从人说回机制", "violet"},
            {"何蔚", "运营负责人", "指标不涨就是没做", "指标口径、落地节奏", "拿结果说话", "rose"}
    };

    private final ObjectMapper objectMapper;

    public OfflineLlmClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public LlmResult chatJson(String systemPrompt, String userPrompt) {
        long startedAt = System.currentTimeMillis();
        String system = systemPrompt == null ? "" : systemPrompt;
        String user = userPrompt == null ? "" : userPrompt;

        String content;
        if (system.contains("你是圆桌会议的策划")) {
            content = analysis(user);
        } else if (user.contains("SEAT_INDEX:")) {
            content = seat(user);
        } else if (system.contains("你是这场圆桌会议的主笔")) {
            content = minutes(user);
        } else {
            content = generic(user);
        }
        int tokens = Math.max(1, content.length() / 2);
        return new LlmResult(content, tokens / 2, tokens, System.currentTimeMillis() - startedAt);
    }

    public LlmResult chatStream(List<ChatMessage> messages, StreamListener listener, CancelToken token) {
        long startedAt = System.currentTimeMillis();
        String system = messages.isEmpty() ? "" : messages.get(0).content();
        String user = messages.size() > 1 ? messages.get(1).content() : "";

        String text;
        if (system.contains("你是这场圆桌的主持人")) {
            text = opening(user);
        } else if (system.contains("你现在是主持人做阶段收束")) {
            text = "分歧点：成本与收益的判断口径不一致。\n新出现的信息：已有案例显示先双跑再灰度更稳。\n下一轮焦点：设定可检验的判断阈值。";
        } else {
            text = speech(system, user);
        }

        listener.onFirstToken(1);
        int size = Math.max(12, text.length() / 6);
        for (int index = 0; index < text.length(); index += size) {
            if (token.isCancelled()) {
                throw LlmException.cancelled();
            }
            listener.onDelta(text.substring(index, Math.min(text.length(), index + size)));
        }
        int tokens = Math.max(1, text.length() / 2);
        return new LlmResult(text, 0, tokens, System.currentTimeMillis() - startedAt);
    }

    private String analysis(String user) {
        String topic = lineAfter(user, "议题：");
        ObjectNode root = objectMapper.createObjectNode();
        root.put("goal", "把「" + topic + "」拆成可判断的分歧与下一步");
        ArrayNode disputes = root.putArray("disputes");
        disputes.add("现在做与半年后做的差别");
        disputes.add("成本与收益是否算得清");
        disputes.add("失败时谁来兜底");
        ArrayNode angles = root.putArray("angles");
        angles.add("成本");
        angles.add("用户价值");
        angles.add("组织落地");
        angles.add("技术可行性");
        ArrayNode audience = root.putArray("audience");
        audience.add("决策者");
        audience.add("执行团队");
        return root.toString();
    }

    private String seat(String user) {
        Matcher matcher = SEAT_INDEX.matcher(user);
        int index = matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
        String[] profile;
        if (user.contains("这一席是主持人")) {
            profile = HOST[0];
        } else {
            profile = EXPERTS[Math.floorMod(index - 1, EXPERTS.length)];
        }
        ObjectNode root = objectMapper.createObjectNode();
        root.put("name", profile[0]);
        root.put("title", profile[1]);
        root.put("stance", profile[2]);
        ArrayNode focus = root.putArray("focus");
        for (String item : profile[3].split("、")) {
            focus.add(item);
        }
        root.put("voice", profile[4]);
        root.put("colorToken", profile[5]);
        return root.toString();
    }

    private String minutes(String user) {
        String topic = lineAfter(user, "议题：");
        String goal = lineAfter(user, "本场目标：");
        List<String> ids = new ArrayList<>();
        Matcher matcher = MESSAGE_ID.matcher(user);
        while (matcher.find() && ids.size() < 20) {
            if (!ids.contains(matcher.group())) {
                ids.add(matcher.group());
            }
        }
        List<String> names = new ArrayList<>();
        for (String[] profile : EXPERTS) {
            if (user.contains(profile[0])) {
                names.add(profile[0]);
            }
        }
        if (names.isEmpty()) {
            names.add("陈立");
        }

        ObjectNode root = objectMapper.createObjectNode();
        root.put("topic", topic);
        root.put("goal", goal.isBlank() ? "围绕议题形成可执行判断" : goal);
        ArrayNode claims = root.putArray("claims");
        for (String name : names) {
            ObjectNode claim = claims.addObject();
            claim.put("name", name);
            ArrayNode points = claim.putArray("points");
            points.add("先明确判断口径，再决定是否投入");
            points.add("设一个可检验的观察点，到点没数据就停");
            ArrayNode sources = claim.putArray("sourceMessageIds");
            ids.stream().limit(2).forEach(sources::add);
        }
        ArrayNode disputes = root.putArray("disputes");
        ObjectNode dispute = disputes.addObject();
        dispute.put("question", "投入与收益的判断口径不一致");
        ObjectNode sideA = dispute.putObject("sideA");
        sideA.putArray("seats").add(names.get(0));
        sideA.put("argument", "先算清成本，避免为不确定的收益买单");
        ids.stream().findFirst().ifPresent(id -> sideA.putArray("sources").add(id));
        ObjectNode sideB = dispute.putObject("sideB");
        sideB.putArray("seats").add(names.get(names.size() - 1));
        sideB.put("argument", "先小步试点，用真实数据替换推演");
        ids.stream().skip(1).findFirst().ifPresent(id -> sideB.putArray("sources").add(id));
        dispute.put("unresolvedBecause", "缺少统一口径下的 A/B 数据");
        root.putArray("consensus").add("不做全量投入，先设观察点");
        ArrayNode hypotheses = root.putArray("hypotheses");
        hypotheses.add("若观察期内关键指标没有改善，则方案不成立");
        ArrayNode nextSteps = root.putArray("nextSteps");
        ObjectNode step = nextSteps.addObject();
        step.put("action", "用 5% 流量做影子实验并约定复盘时间");
        step.put("proposedBy", names.get(0));
        return root.toString();
    }

    private String opening(String user) {
        String topic = lineAfter(user, "【议题】");
        String goal = lineAfter(user, "【本场目标】");
        String question = goal.isBlank() ? topic : goal;
        return "今天只回答一个问题：" + topic + "。本场要回答的是：" + question
                + "。我会让每位嘉宾从自己的立场出发，把分歧摆到桌面上，先给判断，再给依据。";
    }

    private String speech(String system, String user) {
        Matcher nameMatcher = NAME.matcher(system);
        String name = nameMatcher.find() ? nameMatcher.group(1).trim() : "这位嘉宾";
        String stance = between(system, "你的立场：", "。");
        String focus = between(system, "你关注的方面：", "。");
        String firstFocus = focus.isBlank() ? "判断口径" : focus.split("、")[0];
        String userText = lineAfter(user, "【用户最新发言 · 必须优先回应】");

        StringBuilder text = new StringBuilder();
        if (!userText.isBlank()) {
            text.append("先回应你刚说的「").append(cut(userText, 24)).append("」。");
        }
        if (!stance.isBlank()) {
            text.append(stance).append("。");
        }
        text.append("从").append(firstFocus).append("看，得先把判断口径划清楚；")
                .append("代价要在进入下一阶段前算明白。")
                .append("我的判断是：先设一个可检验的观察点，到点没数据就停。");
        if (name.equals("这位嘉宾")) {
            text.insert(0, "我补一句：");
        }
        return cut(text.toString(), 180);
    }

    private String generic(String user) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("content", cut(user.isBlank() ? "本场暂无可整理的内容" : user, 200));
        return root.toString();
    }

    private static String lineAfter(String text, String marker) {
        if (text == null) {
            return "";
        }
        int start = text.indexOf(marker);
        if (start < 0) {
            return "";
        }
        String rest = text.substring(start + marker.length());
        int end = rest.indexOf('\n');
        return (end < 0 ? rest : rest.substring(0, end)).trim();
    }

    private static String between(String text, String startMarker, String endMarker) {
        int start = text.indexOf(startMarker);
        if (start < 0) {
            return "";
        }
        String rest = text.substring(start + startMarker.length());
        int end = rest.indexOf(endMarker);
        return (end < 0 ? rest : rest.substring(0, end)).trim();
    }

    private static String cut(String text, int max) {
        String value = text.replace('\n', ' ').trim();
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }
}
