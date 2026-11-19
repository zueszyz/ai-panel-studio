package com.panelstudio.api;

import com.panelstudio.api.dto.SessionDtos.EndResponse;
import com.panelstudio.api.dto.SessionDtos.PostMessageRequest;
import com.panelstudio.api.dto.SessionDtos.PostMessageResponse;
import com.panelstudio.api.dto.SessionDtos.RetryMessageResponse;
import com.panelstudio.api.dto.SessionDtos.RunsResponse;
import com.panelstudio.api.dto.SessionDtos.StartResponse;
import com.panelstudio.api.dto.SessionDtos.TurnResponse;
import com.panelstudio.application.session.DiscussionSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 讨论现场：开场 / 事件流 / 发言 / 推进回合 / 结束 / 轨迹（docs/API.md §2.5–§2.11）。 */
@RestController
@RequestMapping("/api/discussions/{id}")
@Tag(name = "讨论现场")
public class DiscussionSessionController {

    private final DiscussionSessionService sessionService;

    public DiscussionSessionController(DiscussionSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping("/start")
    @Operation(summary = "开场（主持人开场白异步推送）")
    public StartResponse start(@PathVariable String id) {
        return sessionService.start(id);
    }

    /**
     * 不声明 produces：错误路径要能协商回 application/json（见 PanelController 注释）。
     */
    @GetMapping("/stream")
    @Operation(summary = "讨论主事件流（单连接多路复用）")
    public SseEmitter stream(@PathVariable String id,
                             @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
                             @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        return sessionService.stream(id, deviceId, lastEventId);
    }

    @PostMapping("/messages")
    @Operation(summary = "用户发言 / 插话（clientMsgId 幂等）")
    public PostMessageResponse postMessage(@PathVariable String id,
                                           @Valid @RequestBody PostMessageRequest request,
                                           @RequestHeader(value = "X-Device-Id", required = false) String deviceId) {
        return sessionService.postMessage(id, request, deviceId);
    }

    @PostMapping("/messages/{messageId}/retry")
    @Operation(summary = "单条失败发言重试（沿用同一 messageId）")
    public RetryMessageResponse retry(@PathVariable String id, @PathVariable String messageId) {
        return sessionService.retry(id, messageId);
    }

    @PostMapping("/turns")
    @Operation(summary = "推进一回合（服务端仍是唯一决策者）")
    public TurnResponse nextTurn(@PathVariable String id) {
        return sessionService.nextTurn(id);
    }

    @PostMapping("/end")
    @Operation(summary = "结束讨论（停止调度并关闭事件流）")
    public EndResponse end(@PathVariable String id) {
        return sessionService.end(id);
    }

    @GetMapping("/runs")
    @Operation(summary = "轨迹回放")
    public RunsResponse runs(@PathVariable String id) {
        return sessionService.runs(id);
    }
}
