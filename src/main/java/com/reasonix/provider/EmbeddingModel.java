package com.reasonix.provider;

/**
 * 统一 Embedding 模型抽象接口。
 *
 * <p>用于文本向量化，对接向量数据库（如 PGVector / Milvus / 内存实现）。
 */
public interface EmbeddingModel {

    /**
     * 将单段文本向量化。
     *
     * @param text 待向量化文本
     * @return 向量数组
     */
    float[] embed(String text);

    /**
     * 批量向量化。
     *
     * @param texts 文本列表
     * @return 向量列表
     */
    java.util.List<float[]> embedAll(java.util.List<String> texts);
}
