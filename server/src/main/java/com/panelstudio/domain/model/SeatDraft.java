package com.panelstudio.domain.model;

import com.panelstudio.domain.enums.AgentRoleType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 席位草稿（尚未落库）：模型产出的原始字段 + 校验。
 * 校验失败不抛异常，返回错误列表，由上层决定「重问一次」还是「用预设兜底」。
 */
public class SeatDraft {

    private static final Set<String> COLOR_TOKENS =
            Set.of("amber", "teal", "indigo", "violet", "rose", "slate", "emerald");

    private String name;
    private String title;
    private String stance;
    private List<String> focus = new ArrayList<>();
    private String voice;
    private String colorToken = "teal";
    private AgentRoleType roleType;

    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (isBlank(name)) {
            errors.add("name 不能为空");
        } else if (name.length() > 12) {
            errors.add("name 过长");
        }
        if (isBlank(title)) {
            errors.add("title 不能为空");
        } else if (title.length() > 20) {
            errors.add("title 过长");
        }
        if (isBlank(stance)) {
            errors.add("stance 不能为空");
        } else if (stance.length() > 60) {
            errors.add("stance 过长");
        }
        if (focus == null || focus.size() < 2 || focus.size() > 4) {
            errors.add("focus 需 2 到 4 个关注点");
        }
        if (isBlank(voice)) {
            errors.add("voice 不能为空");
        } else if (voice.length() > 40) {
            errors.add("voice 过长");
        }
        return errors;
    }

    public void normalize() {
        this.name = trim(name);
        this.title = trim(title);
        this.stance = trim(stance);
        this.voice = trim(voice);
        this.colorToken = COLOR_TOKENS.contains(colorToken) ? colorToken : "teal";
        if (focus == null) {
            focus = new ArrayList<>();
        }
        focus = focus.stream().filter(item -> !isBlank(item)).map(String::trim).distinct().toList();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getStance() {
        return stance;
    }

    public void setStance(String stance) {
        this.stance = stance;
    }

    public List<String> getFocus() {
        return focus;
    }

    public void setFocus(List<String> focus) {
        this.focus = focus;
    }

    public String getVoice() {
        return voice;
    }

    public void setVoice(String voice) {
        this.voice = voice;
    }

    public String getColorToken() {
        return colorToken;
    }

    public void setColorToken(String colorToken) {
        this.colorToken = colorToken;
    }

    public AgentRoleType getRoleType() {
        return roleType;
    }

    public void setRoleType(AgentRoleType roleType) {
        this.roleType = roleType;
    }
}
