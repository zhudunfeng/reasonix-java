package com.reasonix.tool.builtin;

import com.reasonix.tool.Tool;
import com.reasonix.tool.ToolContext;
import com.reasonix.tool.ToolExecutionResult;
import org.springframework.stereotype.Component;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 文件模式匹配工具 - 按 glob 模式匹配文件名
 * readOnly=true
 */
@Component
public class GlobTool implements Tool {
    @Override
    public String name() {
        return "glob";
    }
    @Override
    public String description() {
        return "文件路径模式匹配。用法: glob{\"pattern\": \"**/*.java\"}";
    }
    @Override
    public Map<String, Object> schema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "pattern", Map.of("type", "string", "description", "glob 匹配模式"),
                "path", Map.of("type", "string", "description", "搜索路径（默认当前目录）")
        ));
        schema.put("required", List.of("pattern"));
        return schema;
    }
    @Override
    public boolean readOnly() {
        return true;
    }
    @Override
    public ToolExecutionResult execute(ToolContext ctx, Map<String, Object> arguments) {
        String pattern = (String) arguments.getOrDefault("pattern", "");
        String path = (String) arguments.getOrDefault("path", ".");

        if (pattern == null || pattern.isBlank()) {
            return ToolExecutionResult.error("缺少匹配模式参数 (pattern)");
        }

        try {
            Path searchPath = ctx.resolvePath(path);
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);

            List<String> results = Files.walk(searchPath)
                    .filter(Files::isRegularFile)
                    .filter(matcher::matches)
                    .map(p -> searchPath.relativize(p).toString())
                    .sorted()
                    .limit(200)
                    .collect(Collectors.toList());

            if (results.isEmpty()) {
                return ToolExecutionResult.success("未找到匹配文件");
            }
            return ToolExecutionResult.success(String.join("\n", results));
        } catch (Exception e) {
            return ToolExecutionResult.error("匹配失败: " + e.getMessage());
        }
    }
}
