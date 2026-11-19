package com.panelstudio.api;

import com.panelstudio.api.dto.DiscussionDtos.ClaimRequest;
import com.panelstudio.api.dto.DiscussionDtos.ClaimResponse;
import com.panelstudio.api.dto.DiscussionDtos.CreatePanelRequest;
import com.panelstudio.api.dto.DiscussionDtos.PanelQueryResponse;
import com.panelstudio.application.DiscussionService;
import com.panelstudio.application.PanelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 组局：席位流式生成（SSE）、查询与认领。 */
@RestController
@RequestMapping("/api/discussions/{id}")
@Tag(name = "组局", description = "议题分析、席位生成与认领")
public class PanelController {

    private final PanelService panelService;
    private final DiscussionService discussionService;

    public PanelController(PanelService panelService, DiscussionService discussionService) {
        this.panelService = panelService;
        this.discussionService = discussionService;
    }

    /**
     * 不声明 produces：SseEmitter 自己会把响应头设成 text/event-stream，
     * 而错误路径（429 REGEN_LIMIT / 409）需要能协商回 application/json。
     */
    @PostMapping("/panel")
    @Operation(summary = "生成席位（逐个席位推送）")
    public SseEmitter generate(@PathVariable("id") String id,
                               @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
                               @RequestBody(required = false) CreatePanelRequest request,
                               HttpServletResponse response) {
        prepareSseResponse(response);
        CreatePanelRequest payload = request == null
                ? new CreatePanelRequest(false, false, null, false)
                : request;
        return panelService.generate(id, payload, deviceId);
    }

    @GetMapping("/panel")
    @Operation(summary = "查询席位（刷新恢复用）")
    public PanelQueryResponse seats(@PathVariable("id") String id,
                                    @RequestHeader(value = "X-Device-Id", required = false) String deviceId) {
        return discussionService.panel(id, deviceId);
    }

    @PostMapping("/claim")
    @Operation(summary = "认领 / 撤销认领席位")
    public ClaimResponse claim(@PathVariable("id") String id,
                               @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
                               @RequestBody ClaimRequest request) {
        return panelService.claim(id, request == null ? null : request.seatId(), deviceId);
    }

    /** SSE 必须关闭缓冲与缓存转换，否则流式会被攒成一次性输出。 */
    private void prepareSseResponse(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
        response.setCharacterEncoding("UTF-8");
    }
}
