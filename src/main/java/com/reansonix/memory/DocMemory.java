package com.reansonix.memory;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 文档记忆（DocMemory）。
 *
 * <p>基于文件系统的文档索引，支持按关键词检索。
 */
@Component
public class DocMemory {

    private final Path root = Paths.get("./workspace/docs").toAbsolutePath().normalize();

    /**
     * 查询文档记忆。
     *
     * @param query 查询文本
     * @param topK  返回条数
     * @return 相关文档片段
     */
    public List<String> query(String query, int topK) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        if (!Files.exists(root)) {
            return Collections.emptyList();
        }
        List<String> results = new ArrayList<>();
        try {
            Files.walk(root).filter(Files::isRegularFile).forEach(path -> {
                try {
                    String content = Files.readString(path, StandardCharsets.UTF_8);
                    if (content.contains(query)) {
                        int idx = content.indexOf(query);
                        int start = Math.max(0, idx - 80);
                        int end = Math.min(content.length(), idx + query.length() + 80);
                        String snippet = content.substring(start, end).replace("\n", " ");
                        results.add(path.getFileName() + ": ..." + snippet + "...");
                    }
                } catch (Exception ignored) {
                }
            });
        } catch (Exception e) {
            // 忽略遍历异常，保持查询稳定
        }
        return results.stream().limit(topK).collect(Collectors.toList());
    }
}
