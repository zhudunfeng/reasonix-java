package com.reasonix.tool;

import java.util.Map;

/**
 * 单条工具调用。
 */
public class ToolCall {

    private final String toolName;
    private final Map<String, Object> arguments;

    public ToolCall(String toolName, Map<String, Object> arguments) {
        this.toolName = toolName;
        this.arguments = arguments != null ? arguments : Map.of();
    }

    public String getToolName() {
        return toolName;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }
}
