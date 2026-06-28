package com.reansonix.provider;

import java.util.List;

/**
 * 对话响应 DTO。
 *
 * <p>封装模型返回内容、工具调用、使用量等元信息。
 */
public class ChatResponse {

    private final String content;
    private final List<ToolCall> toolCalls;
    private final String finishReason;
    private final Usage usage;

    public ChatResponse(String content, List<ToolCall> toolCalls, String finishReason, Usage usage) {
        this.content = content;
        this.toolCalls = toolCalls;
        this.finishReason = finishReason;
        this.usage = usage;
    }

    public String getContent() {
        return content;
    }

    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    public String getFinishReason() {
        return finishReason;
    }

    public Usage getUsage() {
        return usage;
    }
}
