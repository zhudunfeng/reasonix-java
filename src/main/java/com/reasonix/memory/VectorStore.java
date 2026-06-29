package com.reasonix.memory;

import java.util.List;

/**
 * 向量存储接口。
 */
public interface VectorStore {

    /**
     * 添加文档。
     *
     * @param id 文档ID
     * @param text 文本内容
     */
    void add(String id, String text);

    /**
     * 查询相似文档。
     *
     * @param query 查询文本
     * @param topK 返回条数
     * @return 相关文档片段
     */
    List<String> query(String query, int topK);
}
