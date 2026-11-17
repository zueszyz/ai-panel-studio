package com.panelstudio.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 全部可调参数集中在此，禁止在代码里出现魔法数值。
 * 取值来自 application.yml，并由环境变量覆盖（见 .env.example）。
 */
@ConfigurationProperties(prefix = "panel")
public class AppProperties {

    private final Web web = new Web();
    private final Llm llm = new Llm();
    private final Executor executor = new Executor();
    private final Concurrency concurrency = new Concurrency();
    private final Timeout timeout = new Timeout();
    private final Sse sse = new Sse();
    private final Panel panel = new Panel();
    private final Budget budget = new Budget();

    public Web getWeb() {
        return web;
    }

    public Llm getLlm() {
        return llm;
    }

    public Executor getExecutor() {
        return executor;
    }

    public Concurrency getConcurrency() {
        return concurrency;
    }

    public Timeout getTimeout() {
        return timeout;
    }

    public Sse getSse() {
        return sse;
    }

    public Panel getPanel() {
        return panel;
    }

    public Budget getBudget() {
        return budget;
    }

    public static class Web {
        /** 允许的前端来源（CORS），不使用 *。 */
        private String origin = "http://localhost:5173";

        public String getOrigin() {
            return origin;
        }

        public void setOrigin(String origin) {
            this.origin = origin;
        }
    }

    public static class Llm {
        private String baseUrl = "";
        private String apiKey = "";
        private String model = "";
        private int maxTokens = 800;
        private double temperature = 0.8;

        /** Key 与地址任一为空即视为未配置，调用方据此降级为预设阵容。 */
        public boolean isConfigured() {
            return baseUrl != null && !baseUrl.isBlank()
                    && apiKey != null && !apiKey.isBlank()
                    && model != null && !model.isBlank();
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }
    }

    public static class Executor {
        private int core = 8;
        private int max = 16;
        private int queue = 200;

        public int getCore() {
            return core;
        }

        public void setCore(int core) {
            this.core = core;
        }

        public int getMax() {
            return max;
        }

        public void setMax(int max) {
            this.max = max;
        }

        public int getQueue() {
            return queue;
        }

        public void setQueue(int queue) {
            this.queue = queue;
        }
    }

    public static class Concurrency {
        private int perRoom = 3;
        private int global = 20;

        public int getPerRoom() {
            return perRoom;
        }

        public void setPerRoom(int perRoom) {
            this.perRoom = perRoom;
        }

        public int getGlobal() {
            return global;
        }

        public void setGlobal(int global) {
            this.global = global;
        }
    }

    public static class Timeout {
        private long firstTokenMs = 45_000L;
        private long totalMs = 90_000L;

        public long getFirstTokenMs() {
            return firstTokenMs;
        }

        public void setFirstTokenMs(long firstTokenMs) {
            this.firstTokenMs = firstTokenMs;
        }

        public long getTotalMs() {
            return totalMs;
        }

        public void setTotalMs(long totalMs) {
            this.totalMs = totalMs;
        }
    }

    public static class Sse {
        private long heartbeatMs = 15_000L;
        private int bufferSize = 500;

        public long getHeartbeatMs() {
            return heartbeatMs;
        }

        public void setHeartbeatMs(long heartbeatMs) {
            this.heartbeatMs = heartbeatMs;
        }

        public int getBufferSize() {
            return bufferSize;
        }

        public void setBufferSize(int bufferSize) {
            this.bufferSize = bufferSize;
        }
    }

    public static class Panel {
        /** 同一场次允许的「换一批」次数上限。 */
        private int regenLimit = 2;
        /** 单回合最多发言人数（K）。 */
        private int speakerLimit = 3;

        public int getRegenLimit() {
            return regenLimit;
        }

        public void setRegenLimit(int regenLimit) {
            this.regenLimit = regenLimit;
        }

        public int getSpeakerLimit() {
            return speakerLimit;
        }

        public void setSpeakerLimit(int speakerLimit) {
            this.speakerLimit = speakerLimit;
        }
    }

    public static class Budget {
        private long discussionTokens = 120_000L;

        public long getDiscussionTokens() {
            return discussionTokens;
        }

        public void setDiscussionTokens(long discussionTokens) {
            this.discussionTokens = discussionTokens;
        }
    }
}
