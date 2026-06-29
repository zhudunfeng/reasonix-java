package com.reasonix.memory;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自动记忆 - 读取 MEMORY.md 并提取可注入 prompt 的摘要
 */
@Component
public class AutoMemory {

    public record MemoryEntry(String content, String source, long timestamp) {
    }

    private final List<MemoryEntry> entries = new ArrayList<>();

    public void loadFromWorkspace(Path workspaceDir) {
        Path memoryFile = workspaceDir.resolve("MEMORY.md");
        if (!Files.exists(memoryFile)) {
            return;
        }

        try {
            String content = Files.readString(memoryFile);
            parseMemoryContent(content);
        } catch (IOException e) {
            System.err.println("加载 MEMORY.md 失败: " + e.getMessage());
        }
    }

    /**
     * 解析 MEMORY.md 内容
     */
    private void parseMemoryContent(String content) {
        String[] lines = content.split(System.lineSeparator());
        StringBuilder currentEntry = new StringBuilder();
        String currentSource = "MEMORY.md";
        Pattern pattern = Pattern.compile("^##\\s+(.+)$");
        for (String line : lines) {
            Matcher m = pattern.matcher(line);
            if (m.find()) {
                if (!currentEntry.isEmpty()) {
                    entries.add(new MemoryEntry(currentEntry.toString().trim(), currentSource, System.currentTimeMillis()));
                }
                currentSource = m.group(1).trim();
            } else {
                currentEntry.append(line).append(System.lineSeparator());
            }
        }
        if (!currentEntry.isEmpty()) {
            entries.add(new MemoryEntry(currentEntry.toString().trim(), currentSource, System.currentTimeMillis()));
        }
    }

    /**
     * 添加记忆条目
     */
    public void addEntry(String content, String source) {
        entries.add(new MemoryEntry(content, source, System.currentTimeMillis()));
    }

    public List<MemoryEntry> getEntries() {
        return List.copyOf(entries);
    }

    /**
     * 查询相关记忆条目。
     *
     * @param query 查询文本
     * @param topK  最多返回条数
     * @return 匹配的记忆片段
     */
    public List<String> query(String query, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        return entries.stream()
                .filter(entry -> entry.content().toLowerCase(Locale.ROOT).contains(lowerQuery))
                .limit(topK)
                .map(entry -> "[" + entry.source() + "] " + entry.content())
                .toList();
    }
}
