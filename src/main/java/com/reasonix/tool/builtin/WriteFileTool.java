package com.reasonix.tool.builtin;

import com.reasonix.tool.Tool;
import com.reasonix.tool.ToolContext;
import com.reasonix.tool.ToolExecutionResult;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * 写入文件工具 - 将内容写入指定路径的文件（完整覆盖）
 * readOnly=false，执行前需通过 Permission Gate 审批
 */
@Component
public class WriteFileTool implements Tool {
    @Override
    public String name() {
        return "write_file";
    }
    @Override
    public String description() {
        return "写入文件（完整覆盖）。用法: write_file{\"path\": \"output.txt\", \"content\": \"文件内容\"}";
    }
    @Override
    public Map<String, Object> schema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "path", Map.of("type", "string", "description", "文件路径"),
                "content", Map.of("type", "string", "description", "要写入的文件内容")
        ));
        schema.put("required", List.of("path", "content"));
        return schema;
    }
    @Override
    public boolean readOnly() {
        return false;
    }
    @Override
    public ToolExecutionResult execute(ToolContext ctx, Map<String, Object> arguments) {
        String path = (String) arguments.getOrDefault("path", "");
        String content = (String) arguments.getOrDefault("content", "");

        if (path == null || path.isBlank()) {
            return ToolExecutionResult.error("缺少文件路径参数 (path)");
        }

        try {
            Path filePath = ctx.resolvePath(path);
            Files.createDirectories(filePath.getParent());

            Files.writeString(filePath, content != null ? content : "", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);

            return ToolExecutionResult.success("文件写入成功: " + path + " (" + (content != null ? content.length() : 0) + " 字节)");
        } catch (Exception e) {
            return ToolExecutionResult.error("写入文件失败: " + e.getMessage());
        }
    }
}
