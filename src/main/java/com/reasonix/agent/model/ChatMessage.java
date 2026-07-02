package com.reasonix.agent.model;

/**
 * 会话中的单条消息。
 *
 * <p>新增可选字段 {@link #toolCallId}，用于在 tool message 中回传 {@code tool_call_id}。</p>
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
