package com.reansonix.provider;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 内存 Embedding 实现占位。
 *
 * <p>通过固定随机种子生成伪向量，仅用于开发阶段占位；生产环境应替换为真实模型调用。
 */
@Component
public class InMemoryEmbeddingModel implements EmbeddingModel {

    private static final int DIM = 16;

    @Override
    public float[] embed(String text) {
        if (text == null) {
            return new float[DIM];
        }
        float[] vector = new float[DIM];
        int seed = text.hashCode();
        for (int i = 0; i < DIM; i++) {
            vector[i] = ((seed ^ (i * 31)) % 1000) / 1000f;
        }
        return vector;
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        if (texts == null) {
            return List.of();
        }
        List<float[]> vectors = new java.util.ArrayList<>(texts.size());
        for (String text : texts) {
            vectors.add(embed(text));
        }
        return vectors;
    }
}
