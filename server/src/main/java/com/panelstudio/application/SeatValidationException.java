package com.panelstudio.application;

import java.util.List;

/** 席位 JSON 结构合法但业务字段不达标：允许上层「带着错误重问一次」。 */
public class SeatValidationException extends RuntimeException {

    private final transient List<String> errors;

    public SeatValidationException(List<String> errors) {
        super(String.join("；", errors));
        this.errors = errors;
    }

    public List<String> errors() {
        return errors;
    }
}
