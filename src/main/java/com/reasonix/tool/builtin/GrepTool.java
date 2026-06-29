package com.reasonix.tool.builtin;

import com.reasonix.tool.Tool;
import com.reasonix.tool.ToolContext;
import com.reasonix.tool.ToolExecutionResult;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

@Component
public class GrepTool implements Tool {
    @Override
    public String name() {
        return "grep";
    }
    @Override
    public String description() {
        return "在文件中搜索匹配正则表达式的行。用法: grep{\"path\": \".\", \"pattern\": \"TODO\", \"include\": \"*.java\"}";
    }
    @Override
    public java.util.Map<String, Object> schema() {
        java.util.Map<String, Object> schema = new java.util.LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", java.util.Map.of(
                "path", java.util.Map.of("type", "string", "description", "搜索路径（文件或目录）"),
                "pattern", java.util.Map.of("type", "string", "description", "正则表达式模式"),
                "include", java.util.Map.of("type", "string", "description", "可选：包含的文件名模式（如 *.java）")
        ));
        schema.put("required", java.util.List.of("pattern"));
        return schema;
    }
    @Override
    public boolean readOnly() {
        return true;
    }
    @Override
    public ToolExecutionResult execute(ToolContext ctx, java.util.Map<String, Object> arguments) {
        String pathStr = (String) arguments.getOrDefault("path", ".");
        String patternStr = (String) arguments.getOrDefault("pattern", "");
        String include = (String) arguments.getOrDefault("include", null);

        if (patternStr == null || patternStr.isBlank()) {
            return ToolExecutionResult.error("缺少正则表达式参数 (pattern)");
        }

        Path searchPath = ctx.resolvePath(pathStr);
        Pattern pattern;
        try {
            pattern = Pattern.compile(patternStr);
        } catch (Exception e) {
            return ToolExecutionResult.error("正则表达式无效: " + e.getMessage());
        }

        List<String> matches = new ArrayList<>();
        try {
            if (Files.isRegularFile(searchPath)) {
                collectMatches(searchPath, pattern, matches);
            } else if (Files.isDirectory(searchPath)) {
                try (var stream = Files.walk(searchPath)) {
                    stream.filter(p -> {
                        if (Files.isDirectory(p)) return false;
                        if (include != null && !include.isBlank()) {
                            String name = p.getFileName().toString();
                            // 简单的通配符匹配
                            String regex = include.replace(".", "\\.").replace("*", ".*");
                            return name.matches(regex);
                        }
                        return true;
                    }).forEach(p -> {
                        try {
                            collectMatches(p, pattern, matches);
                        } catch (Exception e) {
                            // 忽略单个文件读取失败，继续处理其他文件
                        }
                    });
                }
            } else {
                return ToolExecutionResult.error("路径不存在: " + pathStr);
            }
        } catch (IOException e) {
            return ToolExecutionResult.error("搜索失败: " + e.getMessage());
        }

        if (matches.isEmpty()) {
            return ToolExecutionResult.success("无匹配结果");
        }
        return ToolExecutionResult.success(String.join("\n", matches));
    }
    private void collectMatches(Path file, Pattern pattern, List<String> matches) {
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                if (pattern.matcher(lines.get(i)).find()) {
                    matches.add(file + ":" + (i + 1) + ":" + lines.get(i));
                }
            }
        } catch (Exception e) {
            // 忽略读取失败，继续处理其他文件
        }
    }
}
