package com.reasonix.tool.builtin;

import com.reasonix.tool.Tool;
import com.reasonix.tool.ToolContext;
import com.reasonix.tool.ToolExecutionResult;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;

@Component
public class ReadFileTool implements Tool {
    @Override
    public String name() {
        return "read_file";
    }
    @Override
    public String description() {
        return "读取文件内容（按 UTF-8 解码）。用法: read_file{\"path\": \"README.md\"}";
    }
    @Override
    public java.util.Map<String, Object> schema() {
        java.util.Map<String, Object> schema = new java.util.LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", java.util.Map.of(
                "path", java.util.Map.of("type", "string", "description", "文件路径")
        ));
        schema.put("required", java.util.List.of("path"));
        return schema;
    }
    @Override
    public boolean readOnly() {
        return true;
    }
    @Override
    public ToolExecutionResult execute(ToolContext ctx, java.util.Map<String, Object> arguments) {
        String path = (String) arguments.getOrDefault("path", "");
        if (path == null || path.isBlank()) {
            return ToolExecutionResult.error("缺少文件路径参数 (path)");
        }
        try {
            Path filePath = ctx.resolvePath(path);
            if (!Files.exists(filePath)) {
                return ToolExecutionResult.error("文件不存在: " + path);
            }
            if (Files.isDirectory(filePath)) {
                return ToolExecutionResult.error("目标是一个目录，不是文件: " + path);
            }
            String content = Files.readString(filePath, StandardCharsets.UTF_8);
            return ToolExecutionResult.success(content);
        } catch (Exception e) {
            return ToolExecutionResult.error("读取文件失败: " + e.getMessage());
        }
    }
}
