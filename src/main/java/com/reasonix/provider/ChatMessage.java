package com.reasonix.provider;

/**
 * 对话消息 DTO。
 *
 * <p>为兼容 OpenAI tool message 格式，新增可选字段 {@link #toolCallId}；构造时若不传入，
 * 该字段为 {@code null}，序列化时将按标准 assistant/tool 消息处理。</p>
 */
public class ChatMessage {

    public enum Role {
        USER, ASSISTANT, SYSTEM, TOOL
    }

    private final Role role;
    private final String content;
    private final String toolCallId;

    /**
     * 兼容旧构造：仅 role + content。
     */
    public ChatMessage(Role role, String content) {
        this(role, content, null);
    }

    public ChatMessage(Role role, String content, String toolCallId) {
        this.role = role;
        this.content = content;
        this.toolCallId = toolCallId;
    }

    public Role getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    /**
     * 返回 tool message 对应的 {@code tool_call_id}；非 tool 消息可能为 {@code null}。
     */
    public String getToolCallId() {
        return toolCallId;
    }
}
