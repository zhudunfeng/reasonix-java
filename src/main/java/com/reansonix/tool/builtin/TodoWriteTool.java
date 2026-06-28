package com.reansonix.tool.builtin;

import com.reansonix.tool.Tool;
import com.reansonix.tool.ToolContext;
import com.reansonix.tool.ToolExecutionResult;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Todo 写入工具 - 管理任务清单状态
 * readOnly=false，但通常作为内部辅助工具使用
 */
@Component
public class TodoWriteTool implements Tool {
    @Override
    public String name() {
        return "todo_write";
    }
    @Override
    public String description() {
        return "写入任务清单。用法: todo_write{\"content\": \"完成 PRD 文档\", \"status\": \"completed\"}";
    }
    @Override
    public Map<String, Object> schema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "content", Map.of("type", "string", "description", "任务描述"),
                "status", Map.of(
                        "type", "string",
                        "enum", List.of("pending", "in_progress", "completed"),
                        "description", "任务状态"
                ),
                "activeForm", Map.of("type", "string", "description", "进行中的描述形式")
        ));
        schema.put("required", List.of("content", "status"));
        return schema;
    }
    @Override
    public boolean readOnly() {
        return false;
    }
    @Override
    public ToolExecutionResult execute(ToolContext ctx, Map<String, Object> arguments) {
        String content = (String) arguments.getOrDefault("content", "");
        String status = (String) arguments.getOrDefault("status", "pending");
        String activeForm = (String) arguments.getOrDefault("activeForm", content);

        if (content == null || content.isBlank()) {
            return ToolExecutionResult.error("缺少任务描述参数 (content)");
        }
        List<Map<String, Object>> todos = (List<Map<String, Object>>) ctx.getMetadata().computeIfAbsent(
                "todos", k -> new ArrayList<>());

        Map<String, Object> todo = new LinkedHashMap<>();
        todo.put("content", content);
        todo.put("status", status);
        todo.put("activeForm", activeForm);
        todo.put("sessionId", ctx.getSessionId());
        todos.add(todo);

        return ToolExecutionResult.success(
                String.format("任务已添加: [%s] %s", status, content));
    }
}
