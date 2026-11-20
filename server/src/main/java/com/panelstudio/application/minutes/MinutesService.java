package com.panelstudio.application.minutes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.panelstudio.api.advice.ApiException;
import com.panelstudio.api.dto.MinuteDtos.DoneEvent;
import com.panelstudio.api.dto.MinuteDtos.FailedEvent;
import com.panelstudio.api.dto.MinuteDtos.MinuteView;
import com.panelstudio.api.dto.MinuteDtos.SectionEvent;
import com.panelstudio.application.DiscussionService;
import com.panelstudio.domain.entity.AgentEntity;
import com.panelstudio.domain.entity.DiscussionEntity;
import com.panelstudio.domain.entity.MessageEntity;
import com.panelstudio.domain.entity.MinuteEntity;
import com.panelstudio.domain.enums.DiscussionStatus;
import com.panelstudio.domain.enums.MessageKind;
import com.panelstudio.domain.enums.MessageRole;
import com.panelstudio.domain.support.IdPrefixes;
import com.panelstudio.domain.support.Timestamps;
import com.panelstudio.domain.support.UlidGenerator;
import com.panelstudio.infra.config.AppProperties;
import com.panelstudio.infra.config.AsyncConfig;
import com.panelstudio.infra.llm.JsonRepair;
import com.panelstudio.infra.llm.LlmClient;
import com.panelstudio.infra.llm.LlmException;
import com.panelstudio.infra.llm.LlmResult;
import com.panelstudio.infra.llm.PromptTemplates;
import com.panelstudio.infra.mapper.AgentMapper;
import com.panelstudio.infra.mapper.DiscussionMapper;
import com.panelstudio.infra.mapper.MessageMapper;
import com.panelstudio.infra.mapper.MinuteMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 收敛模块：把一场讨论整理成六段式纪要，分节流式推送并版本化落库（PRD FR-7）。
 *
 * <p>三条硬约束：</p>
 * <ul>
 *   <li>只读本场消息；模型返回的 {@code sourceMessageIds} 必须属于本场，否则剔除（隔离要求）；</li>
 *   <li>失败不覆盖旧版本：成功才写 READY 行，失败只回报 {@code keptVersion}；</li>
 *   <li>不在事务里调模型：生成在事务外，落库是一句 INSERT。</li>
 * </ul>
 */
@Service
public class MinutesService {

    private static final Logger log = LoggerFactory.getLogger(MinutesService.class);

    /** 传给模型的上下文上限：足够复述分歧，又不至于把预算烧穿。 */
    private static final int MAX_MESSAGES = 80;
    private static final int MAX_TRANSCRIPT_CHARS = 12_000;
    private static final int MAX_POINTS = 4;
    private static final int MAX_POINT_CHARS = 80;
    private static final int MAX_ITEMS = 6;
    private static final String LOCAL_MODEL = "local-fallback";

    private static final List<String> SECTION_ORDER =
            List.of("topic", "claims", "disputes", "consensus", "hypotheses", "nextSteps");
    private static final Map<String, String> SECTION_TITLES = Map.of(
            "topic", "一、议题与目标",
            "claims", "二、各方核心主张",
            "disputes", "三、分歧点",
            "consensus", "四、已达成的共识",
            "hypotheses", "五、待验证假设",
            "nextSteps", "六、建议下一步");

    private final DiscussionService discussionService;
    private final DiscussionMapper discussionMapper;
    private final AgentMapper agentMapper;
    private final MessageMapper messageMapper;
    private final MinuteMapper minuteMapper;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final AppProperties properties;
    private final UlidGenerator idGenerator;
    private final ThreadPoolTaskExecutor agentExecutor;

    public MinutesService(DiscussionService discussionService,
                          DiscussionMapper discussionMapper,
                          AgentMapper agentMapper,
                          MessageMapper messageMapper,
                          MinuteMapper minuteMapper,
                          LlmClient llmClient,
                          ObjectMapper objectMapper,
                          AppProperties properties,
                          UlidGenerator idGenerator,
                          @Qualifier(AsyncConfig.AGENT_EXECUTOR) ThreadPoolTaskExecutor agentExecutor) {
        this.discussionService = discussionService;
        this.discussionMapper = discussionMapper;
        this.agentMapper = agentMapper;
        this.messageMapper = messageMapper;
        this.minuteMapper = minuteMapper;
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.idGenerator = idGenerator;
        this.agentExecutor = agentExecutor;
    }

    // ── 生成（SSE） ──────────────────────────────────────────

    public SseEmitter generate(String discussionId, boolean interim) {
        DiscussionEntity discussion = discussionService.requireDiscussion(discussionId);
        requireSummarizable(discussion, interim);
        SseEmitter emitter = new SseEmitter(0L);
        agentExecutor.execute(() -> run(emitter, discussion, interim));
        return emitter;
    }

    private void requireSummarizable(DiscussionEntity discussion, boolean interim) {
        DiscussionStatus status = discussion.getStatus();
        // 阶段性小结（PRD FR-7）：讨论中也能看，但不入库、不占版本、不收敛状态。
        if (interim) {
            return;
        }
        // 正式纪要先结束讨论；DRAFT/PANEL_READY/FAILED 没有可整理的内容。
        if (status == DiscussionStatus.LIVE
                || status == DiscussionStatus.DRAFT
                || status == DiscussionStatus.PANEL_READY
                || status == DiscussionStatus.FAILED) {
            throw new ApiException(HttpStatus.CONFLICT, "DISCUSSION_STATE_INVALID",
                    "讨论还没结束，先结束讨论再生成纪要；也可以生成阶段性小结",
                    Map.of("from", status.name(), "to", DiscussionStatus.CLOSED.name()));
        }
    }

    private void run(SseEmitter emitter, DiscussionEntity discussion, boolean interim) {
        String discussionId = discussion.getId();
        try {
            List<AgentEntity> seats = agentMapper.findByDiscussionId(discussionId);
            List<MessageEntity> messages = messageMapper.findByDiscussionId(discussionId);
            if (messages.isEmpty()) {
                failed(emitter, "MINUTES_EMPTY", "这场圆桌还没有可整理的发言", false, latestVersion(discussionId));
                return;
            }
            // 只有用户提问、没有专家发言时，硬凑一份纪要等于编造结论：直接说清楚
            if (!hasSpeech(messages)) {
                failed(emitter, "MINUTES_EMPTY", "这场圆桌还没有专家发言，暂时无法整理纪要",
                        false, latestVersion(discussionId));
                return;
            }

            String topic = discussion.getTopic();
            String goal = discussion.getGoal();

            // 第一段不依赖模型：先把"议题与目标"推出去，避免用户对着空白等待（首帧 < 1s）
            String topicMd = sectionMd("topic", topic + "\n\n" + (goal == null || goal.isBlank() ? "" : goal));
            send(emitter, "minutes.section", new SectionEvent("topic", 0, topicMd, interim));

            boolean local = !properties.getLlm().isConfigured();
            ObjectNode structured;
            int tokens;
            String model;
            if (local) {
                structured = composeLocally(discussion, seats, messages);
                tokens = 0;
                model = LOCAL_MODEL;
            } else {
                ModelResult result = requestFromModel(discussion, seats, messages);
                structured = result.structured();
                tokens = result.tokens();
                model = properties.getLlm().getModel();
            }
            enrichMeta(structured, discussion, seats, messages);

            Map<String, AgentEntity> seatById = seats.stream()
                    .collect(Collectors.toMap(AgentEntity::getId, seat -> seat, (left, right) -> left));
            Map<String, String> sections = renderSections(structured, seatById);

            // 阶段性小结不落库：id 仅用于前端定位，version 沿用当前已落库版本（没有则为 0）。
            String minuteId;
            int version;
            if (interim) {
                minuteId = idGenerator.nextWithPrefix(IdPrefixes.MINUTE);
                Integer latest = latestVersion(discussionId);
                version = latest == null ? 0 : latest;
            } else {
                MinuteEntity saved = persist(discussionId, topic, structured, sections, model, tokens);
                minuteId = saved.getId();
                version = saved.getVersion();
            }

            int index = 1;
            for (String key : SECTION_ORDER) {
                if ("topic".equals(key)) {
                    continue;
                }
                send(emitter, "minutes.section", new SectionEvent(key, index, sections.get(key), interim));
                index++;
            }
            // 先收敛再报完成：否则前端收到 done 后立刻刷新，会读到还没迁到 CLOSED 的旧状态。
            DiscussionStatus finalStatus = discussion.getStatus();
            if (!interim) {
                try {
                    closeOnSuccess(discussionId);
                    finalStatus = discussionService.requireDiscussion(discussionId).getStatus();
                } catch (RuntimeException ex) {
                    // 纪要已经落库，收敛失败只影响状态徽标，不能因此把成功报成失败。
                    log.warn("纪要已落库，但讨论状态收敛失败 discussionId={}", discussionId, ex);
                }
            }
            send(emitter, "minutes.done", new DoneEvent(minuteId, version, tokens,
                    local ? "LOCAL" : null, interim, finalStatus));
            emitter.complete();
            log.info("minutes.done discussionId={} version={} local={} interim={}",
                    discussionId, version, local, interim);
        } catch (ClientGoneException ex) {
            log.debug("纪要流客户端已断开 discussionId={}", discussionId);
            emitter.complete();
        } catch (LlmException ex) {
            log.warn("纪要生成失败 discussionId={} code={}", discussionId, ex.code());
            failed(emitter, ex.code(), ex.getMessage(), ex.retriable(), latestVersion(discussionId));
        } catch (RuntimeException ex) {
            log.error("纪要生成异常 discussionId={}", discussionId, ex);
            failed(emitter, "INTERNAL_ERROR", "生成纪要时出错，请重试", true, latestVersion(discussionId));
        }
    }

    private record ModelResult(ObjectNode structured, int tokens) {
    }

    /** 模型调用 + 一次修复性重问（rules/30 §6）：失败重问，而不是复用脏结果。 */
    private ModelResult requestFromModel(DiscussionEntity discussion, List<AgentEntity> seats,
                                         List<MessageEntity> messages) {
        String systemPrompt = PromptTemplates.minutesSystem();
        String userPrompt = PromptTemplates.minutesUser(discussion.getTopic(), discussion.getGoal(),
                seatDigest(seats), transcript(messages, seats));
        LlmException last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                String prompt = attempt == 0
                        ? userPrompt
                        : userPrompt + "\n上一次输出不是合法 JSON。这次只输出 JSON，不要任何解释与代码围栏。";
                LlmResult result = llmClient.chatJson(systemPrompt, prompt);
                ObjectNode structured = sanitize(parse(result.content()), seats, messages);
                return new ModelResult(structured, result.tokensIn() + result.tokensOut());
            } catch (LlmException ex) {
                if (!ex.retriable()) {
                    throw ex;
                }
                last = ex;
                log.debug("纪要第 {} 次生成失败 code={}", attempt + 1, ex.code());
            }
        }
        throw last == null ? LlmException.jsonInvalid() : last;
    }

    /** 至少有一条"AI 说出来的完整内容"，否则纪要没有可整理的原料。 */
    private boolean hasSpeech(List<MessageEntity> messages) {
        return messages.stream().anyMatch(message -> message.getAgentId() != null
                && message.getContent() != null && !message.getContent().isBlank()
                && message.getStatus() != null
                && message.getStatus() != com.panelstudio.domain.enums.MessageStatus.FAILED);
    }

    private JsonNode parse(String raw) {
        try {
            return objectMapper.readTree(JsonRepair.repair(raw));
        } catch (Exception ex) {
            throw LlmException.jsonInvalid();
        }
    }

    // ── 结构化装配与校验 ─────────────────────────────────────

    /**
     * 清洗模型输出：席位必须属于本场、消息 id 必须能在本场找到，
     * 数量与长度做上限裁剪。宁可少一条，也不让纪要张冠李戴。
     */
    private ObjectNode sanitize(JsonNode raw, List<AgentEntity> seats, List<MessageEntity> messages) {
        Map<String, AgentEntity> seatById = seats.stream()
                .collect(Collectors.toMap(AgentEntity::getId, seat -> seat, (left, right) -> left));
        Map<String, AgentEntity> seatByName = seats.stream()
                .collect(Collectors.toMap(AgentEntity::getName, seat -> seat, (left, right) -> left));
        Set<String> messageIds = messages.stream().map(MessageEntity::getId).collect(Collectors.toSet());

        ObjectNode root = objectMapper.createObjectNode();
        root.put("topic", text(raw.path("topic"), 200));
        root.put("goal", text(raw.path("goal"), 200));

        ArrayNode claims = root.putArray("claims");
        for (JsonNode node : array(raw.path("claims"))) {
            // 模型经常把 id 写错但姓名是对的：先按 id 认，认不出再按姓名认，仍认不出就丢弃
            AgentEntity seat = seatById.get(node.path("seatId").asText(""));
            if (seat == null) {
                seat = seatByName.get(node.path("name").asText(""));
            }
            if (seat == null) {
                continue;
            }
            ObjectNode claim = claims.addObject();
            claim.put("seatId", seat.getId());
            claim.put("name", seat.getName());
            ArrayNode points = claim.putArray("points");
            texts(node.path("points"), MAX_POINTS, MAX_POINT_CHARS).forEach(points::add);
            if (points.isEmpty()) {
                claims.remove(claims.size() - 1);
                continue;
            }
            ArrayNode sources = claim.putArray("sourceMessageIds");
            keepKnown(node.path("sourceMessageIds"), messageIds).forEach(sources::add);
        }

        ArrayNode disputes = root.putArray("disputes");
        for (JsonNode node : array(raw.path("disputes"))) {
            String question = text(node.path("question"), 120);
            if (question.isBlank()) {
                continue;
            }
            ObjectNode dispute = disputes.addObject();
            dispute.put("question", question);
            side(dispute.putObject("sideA"), node.path("sideA"), seatById, messageIds);
            side(dispute.putObject("sideB"), node.path("sideB"), seatById, messageIds);
            dispute.put("unresolvedBecause", text(node.path("unresolvedBecause"), 120));
        }

        ArrayNode consensus = root.putArray("consensus");
        texts(raw.path("consensus"), MAX_ITEMS, MAX_POINT_CHARS).forEach(consensus::add);

        ArrayNode hypotheses = root.putArray("hypotheses");
        texts(raw.path("hypotheses"), MAX_ITEMS, MAX_POINT_CHARS).forEach(hypotheses::add);

        ArrayNode nextSteps = root.putArray("nextSteps");
        for (JsonNode node : array(raw.path("nextSteps"))) {
            String action = text(node.path("action"), 120);
            if (action.isBlank()) {
                continue;
            }
            ObjectNode step = nextSteps.addObject();
            step.put("action", action);
            step.put("proposedBy", text(node.path("proposedBy"), 20));
        }

        if (claims.isEmpty()) {
            throw LlmException.jsonInvalid();
        }
        return root;
    }

    private void side(ObjectNode target, JsonNode node, Map<String, AgentEntity> seatById,
                      Set<String> messageIds) {
        ArrayNode seats = target.putArray("seats");
        keepKnown(node.path("seats"), seatById.keySet()).forEach(seats::add);
        target.put("argument", text(node.path("argument"), 160));
        ArrayNode sources = target.putArray("sources");
        keepKnown(node.path("sources"), messageIds).forEach(sources::add);
    }

    /** 无模型密钥时的离线摘要：只从本场真实消息里抽取，不虚构观点。 */
    private ObjectNode composeLocally(DiscussionEntity discussion, List<AgentEntity> seats,
                                      List<MessageEntity> messages) {
        Map<String, AgentEntity> seatById = seats.stream()
                .collect(Collectors.toMap(AgentEntity::getId, seat -> seat, (left, right) -> left));
        ObjectNode root = objectMapper.createObjectNode();
        root.put("topic", discussion.getTopic());
        root.put("goal", discussion.getGoal() == null || discussion.getGoal().isBlank()
                ? "围绕议题形成可执行判断" : discussion.getGoal());
        root.put("fallback", "LOCAL");

        ArrayNode claimArray = root.putArray("claims");
        Map<String, List<MessageEntity>> bySeat = new LinkedHashMap<>();
        for (MessageEntity message : messages) {
            if (message.getAgentId() == null || message.getRole() == MessageRole.USER) {
                continue;
            }
            if (message.getKind() != MessageKind.SPEECH && message.getKind() != MessageKind.OPENING) {
                continue;
            }
            bySeat.computeIfAbsent(message.getAgentId(), key -> new ArrayList<>()).add(message);
        }
        for (Map.Entry<String, List<MessageEntity>> entry : bySeat.entrySet()) {
            AgentEntity seat = seatById.get(entry.getKey());
            if (seat == null) {
                continue;
            }
            ObjectNode claim = claimArray.addObject();
            claim.put("seatId", seat.getId());
            claim.put("name", seat.getName());
            ArrayNode points = claim.putArray("points");
            ArrayNode sources = claim.putArray("sourceMessageIds");
            for (MessageEntity message : entry.getValue().subList(0, Math.min(2, entry.getValue().size()))) {
                String point = firstSentence(message.getContent(), MAX_POINT_CHARS);
                if (!point.isBlank()) {
                    points.add(point);
                    sources.add(message.getId());
                }
            }
            if (points.isEmpty()) {
                claimArray.remove(claimArray.size() - 1);
            }
        }

        ArrayNode disputes = root.putArray("disputes");
        if (claimArray.size() >= 2) {
            JsonNode left = claimArray.get(0);
            JsonNode right = claimArray.get(1);
            ObjectNode dispute = disputes.addObject();
            dispute.put("question", "围绕「" + shorten(discussion.getTopic(), 30) + "」各方取舍是否成立");
            ObjectNode sideA = dispute.putObject("sideA");
            sideA.putArray("seats").add(left.path("seatId").asText());
            sideA.put("argument", left.path("points").path(0).asText());
            sideA.putArray("sources").add(left.path("sourceMessageIds").path(0).asText());
            ObjectNode sideB = dispute.putObject("sideB");
            sideB.putArray("seats").add(right.path("seatId").asText());
            sideB.put("argument", right.path("points").path(0).asText());
            sideB.putArray("sources").add(right.path("sourceMessageIds").path(0).asText());
            dispute.put("unresolvedBecause", "讨论中尚未收敛，需要更多数据验证");
        }

        ArrayNode consensus = root.putArray("consensus");
        ArrayNode hypotheses = root.putArray("hypotheses");
        ArrayNode nextSteps = root.putArray("nextSteps");
        for (MessageEntity message : messages) {
            if (message.getKind() != MessageKind.DIGEST) {
                continue;
            }
            for (String line : message.getContent().split("\\n")) {
                String cleaned = line.replaceFirst("^[-·•\\d.、\\s]+", "").trim();
                if (cleaned.isEmpty()) {
                    continue;
                }
                if (hypotheses.size() < MAX_ITEMS && (cleaned.contains("若") || cleaned.contains("如果"))) {
                    hypotheses.add(shorten(cleaned, MAX_POINT_CHARS));
                } else if (consensus.size() < MAX_ITEMS
                        && (cleaned.contains("一致") || cleaned.contains("共识"))) {
                    consensus.add(shorten(cleaned, MAX_POINT_CHARS));
                }
            }
        }
        return root;
    }

    // ── Markdown 渲染与落库 ──────────────────────────────────

    /**
     * 参会信息不交给模型：时长、回合数、席位名单与用户关切都从本场数据直接算，
     * 避免模型编造"共 5 位专家"或漏掉用户真正问过的问题。
     */
    private void enrichMeta(ObjectNode structured, DiscussionEntity discussion,
                            List<AgentEntity> seats, List<MessageEntity> messages) {
        ObjectNode meta = structured.putObject("meta");
        int rounds = messages.stream()
                .mapToInt(message -> message.getRoundNo() == null ? 0 : message.getRoundNo())
                .max().orElse(0);
        meta.put("rounds", rounds);
        meta.put("seatCount", seats.size());
        meta.put("messageCount", messages.size());
        meta.put("durationSec", durationSec(discussion));
        meta.put("generatedAt", Timestamps.now());
        ArrayNode participants = meta.putArray("participants");
        for (AgentEntity seat : seats) {
            participants.add(seat.getName() + "（" + seat.getTitle() + "）");
        }
        ArrayNode questions = meta.putArray("userQuestions");
        Set<String> seen = new LinkedHashSet<>();
        for (MessageEntity message : messages) {
            if (message.getRole() != MessageRole.USER) {
                continue;
            }
            String question = shorten(message.getContent(), MAX_POINT_CHARS);
            if (!question.isBlank() && seen.add(question) && questions.size() < 5) {
                questions.add(question);
            }
        }
    }

    private int durationSec(DiscussionEntity discussion) {
        String start = discussion.getStartedAt() == null ? discussion.getCreatedAt() : discussion.getStartedAt();
        String end = discussion.getEndedAt() == null ? Timestamps.now() : discussion.getEndedAt();
        if (start == null || end == null) {
            return 0;
        }
        try {
            return (int) Math.max(0, Duration.between(Instant.parse(start), Instant.parse(end)).getSeconds());
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    private Map<String, String> renderSections(ObjectNode structured, Map<String, AgentEntity> seatById) {
        Map<String, String> sections = new LinkedHashMap<>();
        sections.put("topic", sectionMd("topic", topicBody(structured)));

        StringBuilder claims = new StringBuilder();
        for (JsonNode claim : structured.path("claims")) {
            List<String> points = new ArrayList<>();
            claim.path("points").forEach(node -> points.add(node.asText()));
            claims.append("- ").append(claim.path("name").asText());
            AgentEntity seat = seatById.get(claim.path("seatId").asText());
            if (seat != null && seat.getTitle() != null && !seat.getTitle().isBlank()) {
                claims.append("（").append(seat.getTitle()).append("）");
            }
            claims.append("：").append(String.join("；", points)).append('\n');
        }
        sections.put("claims", sectionMd("claims", claims.toString().stripTrailing()));

        StringBuilder disputes = new StringBuilder();
        for (JsonNode dispute : structured.path("disputes")) {
            disputes.append("- 分歧内容：").append(dispute.path("question").asText()).append('\n');
            disputes.append("  - A 方论据：").append(dispute.path("sideA").path("argument").asText("")).append('\n');
            disputes.append("  - B 方论据：").append(dispute.path("sideB").path("argument").asText("")).append('\n');
            String unresolved = dispute.path("unresolvedBecause").asText("");
            if (!unresolved.isBlank()) {
                disputes.append("  - 当前未决原因：").append(unresolved).append('\n');
            }
        }
        sections.put("disputes", sectionMd("disputes", disputes.toString().stripTrailing()));

        sections.put("consensus", sectionMd("consensus", bullet(structured.path("consensus"))));
        sections.put("hypotheses", sectionMd("hypotheses", bullet(structured.path("hypotheses"))));

        StringBuilder steps = new StringBuilder();
        for (JsonNode step : structured.path("nextSteps")) {
            steps.append("- ").append(step.path("action").asText());
            String by = step.path("proposedBy").asText("");
            if (!by.isBlank()) {
                steps.append("（").append(by).append("）");
            }
            steps.append('\n');
        }
        sections.put("nextSteps", sectionMd("nextSteps", steps.toString().stripTrailing()));
        return sections;
    }

    private String topicBody(ObjectNode structured) {
        StringBuilder builder = new StringBuilder(
                joinNonBlank(structured.path("topic").asText(), structured.path("goal").asText()));
        JsonNode meta = structured.path("meta");
        JsonNode participants = meta.path("participants");
        if (participants.isArray() && !participants.isEmpty()) {
            List<String> names = new ArrayList<>();
            participants.forEach(node -> names.add(node.asText()));
            builder.append("\n\n**参会席位**：").append(String.join("、", names));
        }
        JsonNode questions = meta.path("userQuestions");
        if (questions.isArray() && !questions.isEmpty()) {
            builder.append("\n\n**用户关切**");
            questions.forEach(node -> builder.append("\n- ").append(node.asText()));
        }
        return builder.toString();
    }

    private String sectionMd(String key, String body) {
        String content = body == null ? "" : body.strip();
        if (content.isEmpty()) {
            content = "（这一节没有可整理的内容）";
        }
        return "### " + SECTION_TITLES.get(key) + "\n" + content;
    }

    private String bullet(JsonNode node) {
        StringBuilder builder = new StringBuilder();
        for (JsonNode item : node) {
            builder.append("- ").append(item.asText()).append('\n');
        }
        return builder.toString().stripTrailing();
    }

    /** 版本号在写入时取 MAX+1；命中唯一索引说明并发重了，重算一次即可。 */
    private MinuteEntity persist(String discussionId, String topic, ObjectNode structured,
                                 Map<String, String> sections, String model, int tokens) {
        StringBuilder markdown = new StringBuilder("# 圆桌纪要：").append(topic).append("\n\n");
        for (String key : SECTION_ORDER) {
            markdown.append(sections.get(key)).append("\n\n");
        }
        String contentMd = markdown.toString().stripTrailing();
        for (int attempt = 0; attempt < 2; attempt++) {
            MinuteEntity entity = new MinuteEntity();
            entity.setId(idGenerator.nextWithPrefix(IdPrefixes.MINUTE));
            entity.setDiscussionId(discussionId);
            entity.setVersion(minuteMapper.maxVersion(discussionId) + 1);
            entity.setContentMd(contentMd);
            entity.setStructuredJson(structured.toString());
            entity.setModel(model);
            entity.setTokensUsed(tokens);
            entity.setStatus("READY");
            try {
                minuteMapper.insert(entity);
                return entity;
            } catch (DuplicateKeyException ex) {
                log.debug("纪要版本冲突，重算一次 discussionId={}", discussionId);
            }
        }
        throw new IllegalStateException("纪要版本写入失败");
    }

    /** 纪要落库成功即视为收敛完成：SUMMARIZING → CLOSED（已是 CLOSED 则忽略）。 */
    private void closeOnSuccess(String discussionId) {
        if (discussionMapper.compareAndSetStatus(discussionId,
                DiscussionStatus.SUMMARIZING, DiscussionStatus.CLOSED, Timestamps.now()) > 0) {
            return;
        }
        // 服务重启后直接出纪要：先回到 SUMMARIZING，再收敛
        if (discussionMapper.compareAndSetStatus(discussionId,
                DiscussionStatus.INTERRUPTED, DiscussionStatus.SUMMARIZING, Timestamps.now()) > 0) {
            discussionMapper.compareAndSetStatus(discussionId,
                    DiscussionStatus.SUMMARIZING, DiscussionStatus.CLOSED, Timestamps.now());
            return;
        }
        log.debug("讨论已不在可收敛状态，跳过 CLOSED 迁移 discussionId={}", discussionId);
    }

    // ── 查询 ────────────────────────────────────────────────

    public MinuteView get(String discussionId, Integer version) {
        discussionService.requireDiscussion(discussionId);
        MinuteEntity entity = version == null
                ? minuteMapper.findLatestReady(discussionId)
                : minuteMapper.findByVersion(discussionId, version);
        if (entity == null || !"READY".equals(entity.getStatus())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MINUTES_NOT_FOUND", "这场圆桌还没有可查看的纪要");
        }
        return new MinuteView(entity.getId(), entity.getDiscussionId(), entity.getVersion(),
                entity.getStatus(), entity.getContentMd(), readStructured(entity.getStructuredJson()),
                entity.getModel(), entity.getTokensUsed() == null ? 0 : entity.getTokensUsed(),
                entity.getCreatedAt(), minuteMapper.findVersions(discussionId));
    }

    private JsonNode readStructured(String json) {
        try {
            return objectMapper.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (Exception ex) {
            log.warn("纪要结构化字段无法解析，返回空对象");
            return objectMapper.createObjectNode();
        }
    }

    private Integer latestVersion(String discussionId) {
        List<Integer> versions = minuteMapper.findVersions(discussionId);
        return versions.isEmpty() ? null : versions.get(0);
    }

    // ── 工具 ────────────────────────────────────────────────

    private String transcript(List<MessageEntity> messages, List<AgentEntity> seats) {
        Map<String, AgentEntity> seatById = seats.stream()
                .collect(Collectors.toMap(AgentEntity::getId, seat -> seat, (left, right) -> left));
        List<MessageEntity> slice = messages.size() > MAX_MESSAGES
                ? messages.subList(messages.size() - MAX_MESSAGES, messages.size())
                : messages;
        StringBuilder builder = new StringBuilder();
        for (MessageEntity message : slice) {
            if (builder.length() > MAX_TRANSCRIPT_CHARS) {
                builder.append("…（更早的发言已省略）\n");
                break;
            }
            AgentEntity seat = message.getAgentId() == null ? null : seatById.get(message.getAgentId());
            String speaker = seat == null
                    ? (message.getRole() == MessageRole.USER ? "你（参与者）" : "旁听者")
                    : seat.getName() + "（" + seat.getTitle() + "）";
            builder.append('[').append(message.getId()).append("] 第 ")
                    .append(message.getRoundNo() == null ? 0 : message.getRoundNo())
                    .append(" 回合 ").append(speaker).append("：")
                    .append(shorten(message.getContent(), 220)).append('\n');
        }
        return builder.toString();
    }

    private String seatDigest(List<AgentEntity> seats) {
        return seats.stream()
                .map(seat -> "· " + seat.getName() + "（" + seat.getTitle() + "），立场：" + seat.getStance())
                .collect(Collectors.joining("\n"));
    }

    private List<JsonNode> array(JsonNode node) {
        List<JsonNode> items = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(items::add);
        }
        return items;
    }

    private List<String> texts(JsonNode node, int max, int maxChars) {
        List<String> values = new ArrayList<>();
        for (JsonNode item : array(node)) {
            String text = shorten(item.asText(""), maxChars);
            if (!text.isBlank() && values.size() < max) {
                values.add(text);
            }
        }
        return values;
    }

    private List<String> keepKnown(JsonNode node, Set<String> known) {
        Set<String> values = new LinkedHashSet<>();
        for (JsonNode item : array(node)) {
            String text = item.asText("");
            if (known.contains(text)) {
                values.add(text);
            }
        }
        return List.copyOf(values);
    }

    private String text(JsonNode node, int maxChars) {
        return shorten(node == null ? "" : node.asText(""), maxChars);
    }

    private String shorten(String value, int maxChars) {
        String flat = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        return flat.length() <= maxChars ? flat : flat.substring(0, maxChars) + "…";
    }

    private String firstSentence(String content, int maxChars) {
        if (content == null) {
            return "";
        }
        String flat = content.replaceAll("\\s+", " ").trim();
        int stop = flat.length();
        for (String mark : new String[]{"。", "！", "？", "；"}) {
            int index = flat.indexOf(mark);
            if (index > 0 && index < stop) {
                stop = index + 1;
            }
        }
        return shorten(flat.substring(0, stop), maxChars);
    }

    private String joinNonBlank(String first, String second) {
        if (second == null || second.isBlank()) {
            return first;
        }
        return first + "\n" + second;
    }

    private void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException ex) {
            throw new ClientGoneException();
        }
    }

    private void failed(SseEmitter emitter, String code, String message, boolean retriable,
                        Integer keptVersion) {
        try {
            emitter.send(SseEmitter.event().name("minutes.failed")
                    .data(new FailedEvent(code, retriable, keptVersion), MediaType.APPLICATION_JSON));
            emitter.complete();
        } catch (IOException | IllegalStateException ignored) {
            emitter.complete();
        }
    }

    /** 客户端断开：停止推送，但不回滚已落库的版本。 */
    private static final class ClientGoneException extends RuntimeException {
    }
}
