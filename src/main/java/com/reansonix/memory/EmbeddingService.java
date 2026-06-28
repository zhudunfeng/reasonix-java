package com.reansonix.memory;

import com.reansonix.provider.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 文本分片工具。
 */
@Component
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final VectorStore vectorStore;

    public EmbeddingService(EmbeddingModel embeddingModel, VectorStore vectorStore) {
        this.embeddingModel = embeddingModel;
        this.vectorStore = vectorStore;
    }

    /**
     * 索引文本。
     *
     * <p>当前版本按固定长度简单分片；后续将接入语义分片。
     */
    public void index(String id, String text, int chunkSize) {
        if (text == null || text.isBlank()) {
            return;
        }
        int size = Math.max(1, chunkSize);
        int index = 0;
        for (int start = 0; start < text.length(); start += size) {
            int end = Math.min(text.length(), start + size);
            String chunk = text.substring(start, end);
            vectorStore.add(id + "-" + index, chunk);
            index++;
        }
    }

    /**
     * 批量索引文本列表。
     */
    public void indexAll(List<String> texts, int chunkSize) {
        if (texts == null) {
            return;
        }
        for (int i = 0; i < texts.size(); i++) {
            index("doc-" + i, texts.get(i), chunkSize);
        }
    }
}
