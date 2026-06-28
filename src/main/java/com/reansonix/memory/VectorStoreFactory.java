package com.reansonix.memory;

import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * 向量存储工厂。
 */
@Component
public class VectorStoreFactory {

    private final InMemoryVectorStore inMemoryVectorStore;

    public VectorStoreFactory(InMemoryVectorStore inMemoryVectorStore) {
        this.inMemoryVectorStore = inMemoryVectorStore;
    }

    /**
     * 获取默认向量存储。
     */
    public VectorStore getDefault() {
        return inMemoryVectorStore;
    }
}
