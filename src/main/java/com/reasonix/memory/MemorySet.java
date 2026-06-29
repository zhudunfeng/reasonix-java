package com.reasonix.memory;

import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 记忆集合（MemorySet）。
 *
 * <p>封装 AutoMemory、DocMemory 等子记忆的容器，提供统一查询接口。
 */
@Component
@Data
public class MemorySet {

    /** 自动记忆（对话摘要等）。 */
    private final AutoMemory autoMemory = new AutoMemory();

    /** 文档记忆（外部知识库等）。 */
    private final DocMemory docMemory = new DocMemory();

    /**
     * 查询相关记忆。
     *
     * @param query 查询文本
     * @param topK  返回条数
     * @return 相关记忆片段
     */
    public List<String> query(String query, int topK) {
        List<String> results = new ArrayList<>();
        results.addAll(autoMemory.query(query, topK));
        results.addAll(docMemory.query(query, topK));
        return results;
    }
}
