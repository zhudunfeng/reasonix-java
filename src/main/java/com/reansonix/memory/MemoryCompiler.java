package com.reansonix.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 记忆编译器（MemoryCompiler）。
 *
 * <p>负责将对话历史、自动记忆与文档记忆整合为可用于上下文的压缩记忆。
 */
@Component
public class MemoryCompiler {
    private static final Logger logger = LoggerFactory.getLogger(MemoryCompiler.class);

    /**
     * 编译记忆。
     *
     * @param autoMemory 自动记忆
     * @param docMemory  文档记忆
     * @param query      查询文本
     * @param topK       返回条数
     * @return 整合后的记忆文本
     */
    public String compile(AutoMemory autoMemory, DocMemory docMemory, String query, int topK) {
        if (query == null || query.isBlank()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        if (autoMemory != null) {
            parts.addAll(autoMemory.query(query, topK));
        }
        if (docMemory != null) {
            parts.addAll(docMemory.query(query, topK));
        }
        if (parts.isEmpty()) {
            return "";
        }
        return String.join("\n", parts);
    }
}
