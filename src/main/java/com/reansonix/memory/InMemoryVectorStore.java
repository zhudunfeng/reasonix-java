package com.reansonix.memory;

import com.reansonix.provider.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存向量存储实现。
 */
@Component
public class InMemoryVectorStore implements VectorStore {

    private static class Entry {
        final String id;
        final String text;
        final float[] vector;

        Entry(String id, String text, float[] vector) {
            this.id = id;
            this.text = text;
            this.vector = vector;
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private final EmbeddingModel embeddingModel;

    public InMemoryVectorStore(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public void add(String id, String text) {
        if (id == null || text == null) {
            return;
        }
        float[] vector = embeddingModel.embed(text);
        entries.add(new Entry(id, text, vector));
    }

    @Override
    public List<String> query(String query, int topK) {
        if (query == null || entries.isEmpty()) {
            return List.of();
        }
        float[] queryVector = embeddingModel.embed(query);
        return entries.stream()
                .sorted((a, b) -> Float.compare(cosine(b.vector, queryVector), cosine(a.vector, queryVector)))
                .limit(topK)
                .map(entry -> entry.id + ": " + entry.text)
                .toList();
    }

    private float cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0f;
        }
        float dot = 0f;
        float normA = 0f;
        float normB = 0f;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        float denom = (float) (Math.sqrt(normA) * Math.sqrt(normB));
        return denom == 0f ? 0f : dot / denom;
    }
}
