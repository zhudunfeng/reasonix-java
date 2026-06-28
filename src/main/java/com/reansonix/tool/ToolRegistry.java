package com.reansonix.tool;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具注册表 - 管理所有已注册工具
 */
@Component
public class ToolRegistry {
    private final Map<String, Tool> tools = new ConcurrentHashMap<>();
    private final List<Tool> registrationOrder = new ArrayList<>();

    public void register(Tool tool) {
        if (tool == null || tool.name() == null) {
            return;
        }
        tools.put(tool.name(), tool);
        registrationOrder.add(tool);
    }

    public Tool get(String name) {
        return tools.get(name);
    }

    public List<Tool> getAll() {
        return new ArrayList<>(registrationOrder);
    }

    public List<String> getNames() {
        return new ArrayList<>(tools.keySet());
    }

    public String getToolSchemasJson() {
        List<Map<String, Object>> schemas = new ArrayList<>();
        for (Tool tool : registrationOrder) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", tool.name());
            entry.put("description", tool.description());
            entry.put("schema", tool.schema());
            entry.put("readOnly", tool.readOnly());
            schemas.add(entry);
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(schemas);
        } catch (Exception e) {
            return "[]";
        }
    }
}
