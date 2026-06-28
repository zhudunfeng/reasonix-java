package com.reansonix.tool;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 写操作审批记录。
 */
public class ToolApproval {

    private final String id;
    private final String toolName;
    private final Map<String, Object> arguments;
    private final String sessionId;
    private final long createdAt;
    private boolean approved;
    private boolean denied;
    private String decisionReason;

    public ToolApproval(String id, String toolName, Map<String, Object> arguments, String sessionId) {
        this.id = id;
        this.toolName = toolName;
        this.arguments = arguments != null ? arguments : Map.of();
        this.sessionId = sessionId;
        this.createdAt = System.currentTimeMillis();
        this.approved = false;
        this.denied = false;
    }

    public String getId() {
        return id;
    }

    public String getToolName() {
        return toolName;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    public String getSessionId() {
        return sessionId;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public boolean isApproved() {
        return approved;
    }

    public boolean isDenied() {
        return denied;
    }

    public void approve(String reason) {
        this.approved = true;
        this.denied = false;
        this.decisionReason = reason;
    }

    public void deny(String reason) {
        this.approved = false;
        this.denied = true;
        this.decisionReason = reason;
    }

    public String getDecisionReason() {
        return decisionReason;
    }
}
