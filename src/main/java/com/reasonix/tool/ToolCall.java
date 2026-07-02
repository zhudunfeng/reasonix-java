package com.reasonix.tool;

import java.util.Map;
import java.util.UUID;

/**
 * 单条工具调用。
 */
public class ToolCall {

    private final String toolName;
    private final Map<String, Object> arguments;
    private final String toolCallId;

    public ToolCall(String toolName, Map<String, Object> arguments) {
        this(toolName, arguments, null);
    }

    public ToolCall(String toolName, Map<String, Object> arguments, String toolCallId) {
        this.toolName = toolName;
        this.arguments = arguments != null ? arguments : Map.of();
        this.toolCallId = toolCallId != null && !toolCallId.isBlank() ? toolCallId : UUID.randomUUID().toString().replace("-", "");
    }

    public String getToolName() {
        return toolName;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    /**
     * 返回本次工具调用的唯一标识，用于 tool result 回写时关联 assistant tool-use 消息。
     */
    public String getToolCallId() {
        return toolCallId;
    }
}
