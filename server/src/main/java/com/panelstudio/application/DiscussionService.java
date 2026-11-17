package com.panelstudio.application;

import com.panelstudio.api.advice.ApiException;
import com.panelstudio.api.dto.DiscussionDtos.CreateDiscussionRequest;
import com.panelstudio.api.dto.DiscussionDtos.CreateDiscussionResponse;
import com.panelstudio.api.dto.DiscussionDtos.DiscussionDetail;
import com.panelstudio.api.dto.DiscussionDtos.DiscussionListResponse;
import com.panelstudio.api.dto.DiscussionDtos.DiscussionSummary;
import com.panelstudio.api.dto.DiscussionDtos.LastMessageView;
import com.panelstudio.api.dto.DiscussionDtos.PanelQueryResponse;
import com.panelstudio.api.dto.DiscussionDtos.SeatBrief;
import com.panelstudio.api.dto.DiscussionDtos.SeatView;
import com.panelstudio.domain.entity.AgentEntity;
import com.panelstudio.domain.entity.DiscussionEntity;
import com.panelstudio.domain.entity.MessageEntity;
import com.panelstudio.domain.entity.ParticipantEntity;
import com.panelstudio.domain.enums.DiscussionStatus;
import com.panelstudio.domain.support.IdPrefixes;
import com.panelstudio.domain.support.UlidGenerator;
import com.panelstudio.infra.mapper.AgentMapper;
import com.panelstudio.infra.mapper.DiscussionMapper;
import com.panelstudio.infra.mapper.MessageMapper;
import com.panelstudio.infra.mapper.ParticipantMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 大厅用例：创建 / 列表 / 详情。只做编排，SQL 在 mapper，状态迁移走状态机。
 */
@Service
public class DiscussionService {

    private static final int DEFAULT_PANEL_SIZE = 4;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;
    private static final int PREVIEW_LENGTH = 42;

    private final DiscussionMapper discussionMapper;
    private final AgentMapper agentMapper;
    private final MessageMapper messageMapper;
    private final ParticipantMapper participantMapper;
    private final UserService userService;
    private final UlidGenerator idGenerator;

    public DiscussionService(DiscussionMapper discussionMapper,
                             AgentMapper agentMapper,
                             MessageMapper messageMapper,
                             ParticipantMapper participantMapper,
                             UserService userService,
                             UlidGenerator idGenerator) {
        this.discussionMapper = discussionMapper;
        this.agentMapper = agentMapper;
        this.messageMapper = messageMapper;
        this.participantMapper = participantMapper;
        this.userService = userService;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public CreateDiscussionResponse create(CreateDiscussionRequest request, String deviceId) {
        String userId = userService.resolveUserId(deviceId);
        DiscussionEntity entity = new DiscussionEntity();
        entity.setId(idGenerator.nextWithPrefix(IdPrefixes.DISCUSSION));
        entity.setTopic(request.topic().trim());
        entity.setStatus(DiscussionStatus.DRAFT);
        entity.setPanelSize(request.panelSize() == null ? DEFAULT_PANEL_SIZE : request.panelSize());
        entity.setTurnIndex(0);
        entity.setCreatedBy(userId);
        discussionMapper.insert(entity);
        return new CreateDiscussionResponse(entity.getId(), entity.getTopic(), entity.getStatus(),
                entity.getPanelSize(), entity.getTurnIndex(), entity.getCreatedAt());
    }

    public DiscussionListResponse list(String statusParam, String keyword, boolean mine, String cursor,
                                       Integer limit, String deviceId) {
        int pageSize = limit == null ? DEFAULT_PAGE_SIZE : Math.min(Math.max(limit, 1), MAX_PAGE_SIZE);
        List<DiscussionStatus> statuses = parseStatuses(statusParam);
        String userId = userService.resolveUserId(deviceId);

        List<String> myIds = null;
        if (mine) {
            if (userId == null) {
                return new DiscussionListResponse(List.of(), null);
            }
            myIds = discussionMapper.findMyDiscussionIds(userId);
        }

        int offset = CursorCodec.decode(cursor);
        String normalizedKeyword = keyword == null ? null : keyword.trim();
        List<DiscussionEntity> rows = discussionMapper.search(statuses, normalizedKeyword, myIds,
                pageSize + 1, offset);

        boolean hasMore = rows.size() > pageSize;
        List<DiscussionEntity> pageRows = hasMore ? rows.subList(0, pageSize) : rows;
        if (pageRows.isEmpty()) {
            return new DiscussionListResponse(List.of(), null);
        }

        List<String> discussionIds = pageRows.stream().map(DiscussionEntity::getId).toList();
        Map<String, List<AgentEntity>> seatsByDiscussion = agentMapper.findByDiscussionIds(discussionIds).stream()
                .collect(Collectors.groupingBy(AgentEntity::getDiscussionId));
        Map<String, String> mySeatByDiscussion = mySeatMap(userId, discussionIds);
        Map<String, MessageEntity> latestByDiscussion = latestMessageMap(discussionIds);
        Map<String, AgentEntity> agentsById = seatsByDiscussion.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toMap(AgentEntity::getId, Function.identity(), (left, right) -> left));

        List<DiscussionSummary> items = new ArrayList<>(pageRows.size());
        for (DiscussionEntity row : pageRows) {
            List<AgentEntity> seats = seatsByDiscussion.getOrDefault(row.getId(), List.of());
            String mySeatId = mySeatByDiscussion.get(row.getId());
            List<SeatBrief> seatBriefs = seats.stream()
                    .map(seat -> new SeatBrief(seat.getId(), seat.getName(), seat.getTitle(),
                            seat.getColorToken(), seat.getStatus(), seat.getId().equals(mySeatId)))
                    .toList();
            MessageEntity latest = latestByDiscussion.get(row.getId());
            LastMessageView lastMessage = latest == null ? null : new LastMessageView(
                    preview(latest.getContent()),
                    speakerName(latest, agentsById),
                    latest.getCreatedAt());
            items.add(new DiscussionSummary(row.getId(), row.getTopic(), row.getStatus(),
                    row.getPanelSize(), row.getTurnIndex(), seatBriefs, lastMessage, row.getUpdatedAt()));
        }
        String nextCursor = hasMore ? CursorCodec.encode(offset + pageSize) : null;
        return new DiscussionListResponse(items, nextCursor);
    }

    public DiscussionDetail detail(String discussionId, String deviceId) {
        DiscussionEntity discussion = requireDiscussion(discussionId);
        String userId = userService.resolveUserId(deviceId);
        return toDetail(discussion, userId);
    }

    public DiscussionDetail toDetail(DiscussionEntity discussion, String userId) {
        List<SeatView> seats = seatViews(discussion.getId(), userId);
        String mySeatId = mySeatMap(userId, List.of(discussion.getId())).get(discussion.getId());
        return new DiscussionDetail(discussion.getId(), discussion.getTopic(), discussion.getGoal(),
                discussion.getStatus(), discussion.getPanelSize(), discussion.getTurnIndex(), seats,
                discussion.getTopicAnalysis(), mySeatId, discussion.getCreatedAt(), discussion.getUpdatedAt());
    }

    public List<SeatView> seatViews(String discussionId, String userId) {
        String mySeatId = mySeatMap(userId, List.of(discussionId)).get(discussionId);
        return agentMapper.findByDiscussionId(discussionId).stream()
                .map(seat -> new SeatView(seat.getId(), seat.getRoleType(), seat.getName(), seat.getTitle(),
                        seat.getStance(), seat.getFocus(), seat.getVoice(), seat.getColorToken(),
                        seat.getOrderIndex(), seat.getStatus(), seat.getId().equals(mySeatId)))
                .toList();
    }

    public PanelQueryResponse panel(String discussionId, String deviceId) {
        DiscussionEntity discussion = requireDiscussion(discussionId);
        String userId = userService.resolveUserId(deviceId);
        return new PanelQueryResponse(discussion.getId(), discussion.getStatus(),
                seatViews(discussion.getId(), userId));
    }

    public DiscussionEntity requireDiscussion(String discussionId) {
        DiscussionEntity discussion = discussionMapper.selectById(discussionId);
        if (discussion == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "DISCUSSION_NOT_FOUND", "这场圆桌不存在");
        }
        return discussion;
    }

    private Map<String, String> mySeatMap(String userId, List<String> discussionIds) {
        if (userId == null || discussionIds.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new HashMap<>();
        for (ParticipantEntity participant : participantMapper.findMySeats(userId, discussionIds)) {
            if (participant.getSeatId() != null) {
                result.put(participant.getDiscussionId(), participant.getSeatId());
            }
        }
        return result;
    }

    private Map<String, MessageEntity> latestMessageMap(List<String> discussionIds) {
        Map<String, MessageEntity> result = new HashMap<>();
        for (MessageEntity message : messageMapper.findLatestPerDiscussion(discussionIds)) {
            MessageEntity existing = result.get(message.getDiscussionId());
            if (existing == null || message.getCreatedAt().compareTo(existing.getCreatedAt()) > 0) {
                result.put(message.getDiscussionId(), message);
            }
        }
        return result;
    }

    private String speakerName(MessageEntity message, Map<String, AgentEntity> agentsById) {
        if (message.getAgentId() == null) {
            return "你";
        }
        AgentEntity agent = agentsById.get(message.getAgentId());
        return agent == null ? "嘉宾" : agent.getName();
    }

    private String preview(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String flat = content.replaceAll("\\s+", " ").trim();
        return flat.length() <= PREVIEW_LENGTH ? flat : flat.substring(0, PREVIEW_LENGTH) + "…";
    }

    private List<DiscussionStatus> parseStatuses(String statusParam) {
        if (statusParam == null || statusParam.isBlank()) {
            return List.of();
        }
        Set<DiscussionStatus> statuses = new LinkedHashSet<>();
        for (String token : statusParam.split(",")) {
            String value = token.trim();
            if (value.isEmpty()) {
                continue;
            }
            try {
                statuses.add(DiscussionStatus.valueOf(value.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "状态取值不合法",
                        Map.of("field", "status"));
            }
        }
        return List.copyOf(statuses);
    }
}
