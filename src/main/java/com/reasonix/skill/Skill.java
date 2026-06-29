package com.reasonix.skill;

import java.util.List;

/**
 * Skill 领域模型 - 描述一个可被发现、加载和执行的技能
 */
public class Skill {

    public enum RunAs {
        INLINE,
        SUBAGENT
    }

    public enum Scope {
        USER,
        PROJECT,
        GLOBAL
    }

    private final String name;
    private final String description;
    private final String body;
    private final Scope scope;
    private final RunAs runAs;
    private final List<String> allowedTools;
    private final String model;
    private final boolean subagent;
    private final String path;
    private final int priority;

    public Skill(String name, String description, String body, Scope scope, RunAs runAs,
                 List<String> allowedTools, String model, boolean subagent, String path, int priority) {
        this.name = name;
        this.description = description;
        this.body = body;
        this.scope = scope;
        this.runAs = runAs;
        this.allowedTools = allowedTools;
        this.model = model;
        this.subagent = subagent;
        this.path = path;
        this.priority = priority;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getBody() {
        return body;
    }

    public Scope getScope() {
        return scope;
    }

    public RunAs getRunAs() {
        return runAs;
    }

    public List<String> getAllowedTools() {
        return allowedTools;
    }

    public String getModel() {
        return model;
    }

    public boolean isSubagent() {
        return subagent;
    }

    public String getPath() {
        return path;
    }

    public int getPriority() {
        return priority;
    }
}
