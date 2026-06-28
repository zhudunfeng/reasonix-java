package com.reansonix.provider;

/**
 * 对话消息 DTO。
 */
public class ChatMessage {

    public enum Role {
        USER, ASSISTANT, SYSTEM, TOOL
    }

    private final Role role;
    private final String content;

    public ChatMessage(Role role, String content) {
        this.role = role;
        this.content = content;
    }

    public Role getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }
}
