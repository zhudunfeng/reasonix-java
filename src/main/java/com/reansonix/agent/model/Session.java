package com.reansonix.agent.model;

import java.time.Instant;

/**
 * 会话实体。
 *
 * <p>保存单次对话的完整状态，包括历史消息、模型标识、压缩标记等。
 */
public class Session {

    private final String sessionId;
    private final java.util.List<ChatMessage> history;
    private String modelId;
    private Instant createdAt;
    private Instant updatedAt;
    private boolean compactTriggered;

    public Session(String sessionId) {
        this.sessionId = sessionId;
        this.history = new java.util.ArrayList<>();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public String getSessionId() {
        return sessionId;
    }

    public java.util.List<ChatMessage> getHistory() {
        return history;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
        touch();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isCompactTriggered() {
        return compactTriggered;
    }

    public void setCompactTriggered(boolean compactTriggered) {
        this.compactTriggered = compactTriggered;
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }
}
