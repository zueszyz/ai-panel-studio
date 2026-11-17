package com.panelstudio.api;

import com.panelstudio.api.dto.DiscussionDtos.CreateDiscussionRequest;
import com.panelstudio.api.dto.DiscussionDtos.CreateDiscussionResponse;
import com.panelstudio.api.dto.DiscussionDtos.DiscussionDetail;
import com.panelstudio.api.dto.DiscussionDtos.DiscussionListResponse;
import com.panelstudio.application.DiscussionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 圆桌大厅：列表 / 创建 / 详情。身份来自 X-Device-Id（缺失即纯匿名浏览）。 */
@RestController
@RequestMapping("/api/discussions")
@Tag(name = "大厅", description = "圆桌列表、创建与详情")
public class DiscussionController {

    private final DiscussionService discussionService;

    public DiscussionController(DiscussionService discussionService) {
        this.discussionService = discussionService;
    }

    @GetMapping
    @Operation(summary = "圆桌列表（状态筛选、议题搜索、分页）")
    public DiscussionListResponse list(
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "q", required = false) String keyword,
            @RequestParam(value = "mine", defaultValue = "false") boolean mine,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", required = false) Integer limit) {
        return discussionService.list(status, keyword, mine, cursor, limit, deviceId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "创建圆桌（议题 + 参会人数）")
    public CreateDiscussionResponse create(
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @Valid @RequestBody CreateDiscussionRequest request) {
        return discussionService.create(request, deviceId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "圆桌详情（席位 + 我是否已认领）")
    public DiscussionDetail detail(
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @PathVariable("id") String id) {
        return discussionService.detail(id, deviceId);
    }
}
