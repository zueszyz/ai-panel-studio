package com.panelstudio.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 单进程演示模式下的 SPA 兜底：直接访问 /discussions/:id 这类前端路由时，
 * 转发到 index.html 交给 Vue Router（避免刷新 404）。
 * 开发模式下页面由 Vite 提供，不会走到这里。
 */
@Controller
public class SpaForwardController {

    @GetMapping({"/discussions/{id}", "/discussions/{id}/panel", "/discussions/{id}/minutes"})
    public String forwardToApp() {
        return "forward:/index.html";
    }
}
