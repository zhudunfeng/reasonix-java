package com.reansonix.tool.builtin;

import com.reansonix.tool.Tool;
import com.reansonix.tool.ToolContext;
import com.reansonix.tool.ToolExecutionResult;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 列出目录内容工具 - 查看指定目录下的文件和文件夹
 * readOnly=true
 */
@Component
public class LsTool implements Tool {
    @Override
    public String name() {
        return "ls";
    }
    @Override
    public String description() {
        return "列出目录内容。用法: ls{\"path\": \"src/main/java\"}";
    }
    @Override
    public Map<String, Object> schema() {
        Map<String, Object> schema = new java.util.LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "path", Map.of("type", "string", "description", "目录路径，默认当前目录")
        ));
        return schema;
    }
    @Override
    public boolean readOnly() {
        return true;
    }
    @Override
    public ToolExecutionResult execute(ToolContext ctx, Map<String, Object> arguments) {
        String path = (String) arguments.getOrDefault("path", ".");
        try {
            Path dirPath = ctx.resolvePath(path);
            if (!Files.isDirectory(dirPath)) {
                return ToolExecutionResult.error("路径不是目录: " + path);
            }

            List<String> entries = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath)) {
                for (Path entry : stream) {
                    String name = entry.getFileName().toString();
                    String suffix = Files.isDirectory(entry) ? "/" : "";
                    entries.add(name + suffix);
                }
            }
            Collections.sort(entries);

            return ToolExecutionResult.success(String.join(System.lineSeparator(), entries));
        } catch (IOException e) {
            return ToolExecutionResult.error("列出目录失败: " + e.getMessage());
        }
    }
}
