package com.panelstudio.domain.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 议题分析结果，落库为 discussions.topic_analysis（TEXT + JacksonTypeHandler）。
 * 用可变 POJO 而不是 record：MyBatis-Plus 的 JacksonTypeHandler 使用原生 ObjectMapper，
 * 反序列化 record 需要额外的参数名模块。
 */
public class TopicAnalysis {

    private String goal = "";
    private List<String> disputes = new ArrayList<>();
    private List<String> angles = new ArrayList<>();
    private List<String> audience = new ArrayList<>();

    public TopicAnalysis() {
    }

    public TopicAnalysis(String goal, List<String> disputes, List<String> angles, List<String> audience) {
        this.goal = goal == null ? "" : goal;
        this.disputes = disputes == null ? new ArrayList<>() : disputes;
        this.angles = angles == null ? new ArrayList<>() : angles;
        this.audience = audience == null ? new ArrayList<>() : audience;
    }

    public String getGoal() {
        return goal;
    }

    public void setGoal(String goal) {
        this.goal = goal;
    }

    public List<String> getDisputes() {
        return disputes;
    }

    public void setDisputes(List<String> disputes) {
        this.disputes = disputes;
    }

    public List<String> getAngles() {
        return angles;
    }

    public void setAngles(List<String> angles) {
        this.angles = angles;
    }

    public List<String> getAudience() {
        return audience;
    }

    public void setAudience(List<String> audience) {
        this.audience = audience;
    }
}
