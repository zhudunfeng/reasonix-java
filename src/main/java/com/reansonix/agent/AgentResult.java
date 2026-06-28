package com.reansonix.agent;

import java.util.List;
import java.util.Map;

/**
 * Agent 执行结果 DTO
 */
public class AgentResult {

    private final String content;
    private final String sessionId;
    private final String modelId;
    private final Map<String, Object> usage;
    private final List<Map<String, Object>> toolCalls;
    private final String error;
    private final boolean compactTriggered;

    private AgentResult(Builder builder) {
        this.content = builder.content;
        this.sessionId = builder.sessionId;
        this.modelId = builder.modelId;
        this.usage = builder.usage;
        this.toolCalls = builder.toolCalls;
        this.error = builder.error;
        this.compactTriggered = builder.compactTriggered;    }

    public String getContent() { return content; }
    public String getSessionId() { return sessionId; }
    public String getModelId() { return modelId; }
    public Map<String, Object> getUsage() { return usage; }
    public List<Map<String, Object>> getToolCalls() { return toolCalls; }
    public String getError() { return error; }
    public boolean isCompactTriggered() { return compactTriggered; }
    public boolean isSuccess() { return error == null; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String content;
        private String sessionId;
        private String modelId;
        private Map<String, Object> usage;
        private List<Map<String, Object>> toolCalls;
        private String error;
        private boolean compactTriggered = false;

        public Builder content(String content) { this.content = content; return this; }
        public Builder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
        public Builder modelId(String modelId) { this.modelId = modelId; return this; }
        public Builder usage(Map<String, Object> usage) { this.usage = usage; return this; }
        public Builder toolCalls(List<Map<String, Object>> toolCalls) { this.toolCalls = toolCalls; return this; }
        public Builder error(String error) { this.error = error; return this; }
        public Builder compactTriggered(boolean compactTriggered) { this.compactTriggered = compactTriggered; return this; }

        public AgentResult build() { return new AgentResult(this); }    }
}
