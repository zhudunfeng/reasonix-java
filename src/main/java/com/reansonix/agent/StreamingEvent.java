package com.reansonix.agent;

import java.util.Map;

/**
 * 流式事件载体 - 通过 SSE 推送到前端
 */
public class StreamingEvent {

    private final StreamingEventType type;
    private final String content;
    private final String toolName;
    private final Map<String, Object> arguments;
    private final String toolResult;
    private final String sessionId;
    private final String modelId;
    private final String errorMessage;
    private final Map<String, Object> usage;
    private final boolean compactTriggered;

    private StreamingEvent(Builder builder) {
        this.type = builder.type;
        this.content = builder.content;
        this.toolName = builder.toolName;
        this.arguments = builder.arguments;
        this.toolResult = builder.toolResult;
        this.sessionId = builder.sessionId;
        this.modelId = builder.modelId;
        this.errorMessage = builder.errorMessage;
        this.usage = builder.usage;
        this.compactTriggered = builder.compactTriggered;    }

    public StreamingEventType getType() { return type; }
    public String getContent() { return content; }
    public String getToolName() { return toolName; }
    public Map<String, Object> getArguments() { return arguments; }
    public String getToolResult() { return toolResult; }
    public String getSessionId() { return sessionId; }
    public String getModelId() { return modelId; }
    public String getErrorMessage() { return errorMessage; }
    public Map<String, Object> getUsage() { return usage; }
    public boolean isCompactTriggered() { return compactTriggered; }

    public static Builder builder(StreamingEventType type) {
        return new Builder(type);    }

    public static class Builder {
        private final StreamingEventType type;
        private String content;
        private String toolName;
        private Map<String, Object> arguments;
        private String toolResult;
        private String sessionId;
        private String modelId;
        private String errorMessage;
        private Map<String, Object> usage;
        private boolean compactTriggered = false;

        Builder(StreamingEventType type) { this.type = type; }

        public Builder content(String content) { this.content = content; return this; }
        public Builder toolName(String toolName) { this.toolName = toolName; return this; }
        public Builder arguments(Map<String, Object> arguments) { this.arguments = arguments; return this; }
        public Builder toolResult(String toolResult) { this.toolResult = toolResult; return this; }
        public Builder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
        public Builder modelId(String modelId) { this.modelId = modelId; return this; }
        public Builder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }
        public Builder usage(Map<String, Object> usage) { this.usage = usage; return this; }
        public Builder compactTriggered(boolean compactTriggered) { this.compactTriggered = compactTriggered; return this; }

        public StreamingEvent build() { return new StreamingEvent(this); }    }
}
