package com.panelstudio.api;

import com.panelstudio.api.dto.MinuteDtos.MinuteView;
import com.panelstudio.application.minutes.MinutesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 会议纪要：生成（SSE 分节）与查询（版本化），对应 docs/API.md §2.9–§2.10。 */
@RestController
@RequestMapping("/api/discussions/{id}/minutes")
@Tag(name = "会议纪要")
public class MinutesController {

    private final MinutesService minutesService;

    public MinutesController(MinutesService minutesService) {
        this.minutesService = minutesService;
    }

    /**
     * 不声明 produces：状态不允许（409）时仍要能协商回 application/json（同 PanelController）。
     */
    @PostMapping
    @Operation(summary = "生成纪要（六段分节流式推送；interim=true 为不入库的阶段性小结）")
    public SseEmitter generate(@PathVariable String id,
                               @RequestParam(value = "interim", defaultValue = "false") boolean interim) {
        return minutesService.generate(id, interim);
    }

    @GetMapping
    @Operation(summary = "查询纪要（默认最新版本，可按版本号取历史）")
    public MinuteView get(@PathVariable String id,
                          @RequestParam(value = "version", required = false) Integer version) {
        return minutesService.get(id, version);
    }
}
