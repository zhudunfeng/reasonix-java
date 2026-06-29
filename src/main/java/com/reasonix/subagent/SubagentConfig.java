package com.reasonix.subagent;

import java.util.List;

/**
 * 子 Agent 配置。
 */
public class SubagentConfig {

    private final String name;
    private final String description;
    private final List<String> tools;
    private final WorkspaceMode workspaceMode;
    private final int maxParallel;

    public SubagentConfig(String name, String description, List<String> tools, WorkspaceMode workspaceMode, int maxParallel) {
        this.name = name;
        this.description = description;
        this.tools = tools;
        this.workspaceMode = workspaceMode;
        this.maxParallel = maxParallel;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getTools() {
        return tools;
    }

    public WorkspaceMode getWorkspaceMode() {
        return workspaceMode;
    }

    public int getMaxParallel() {
        return maxParallel;
    }
}
