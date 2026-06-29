package com.reasonix.provider;

import com.reasonix.config.ReasonixConfig;
import org.springframework.stereotype.Component;

/**
 * 模型工厂。
 *
 * <p>根据 {@link ReasonixConfig} 中的供应商与模型定义，创建对应的 {@link ChatModel} 与 {@link EmbeddingModel}。
 */
@Component
public class ModelFactory {

    private final ModelRegistry modelRegistry;

    public ModelFactory(ModelRegistry modelRegistry) {
        this.modelRegistry = modelRegistry;
    }

    /**
     * 根据模型 ID 创建 {@link ChatModel}。
     *
     * <p>当前版本仅提供基础抽象与占位实现；后续将接入真实 HTTP 调用。
     */
    public ChatModel createChatModel(String modelId) {
        modelRegistry.getModel(modelId).orElseThrow(() ->
                new IllegalArgumentException("未找到模型定义: " + modelId));
        // 占位：返回基于 modelId 的适配器
        return new OpenAiCompatibleChatModel(modelId);
    }

    /**
     * 根据模型 ID 创建 {@link EmbeddingModel}。
     *
     * <p>当前版本仅提供基础抽象与占位实现；后续将接入真实 HTTP 调用。
     */
    public EmbeddingModel createEmbeddingModel(String modelId) {
        // 占位：返回内存 Embedding 实现
        return new InMemoryEmbeddingModel();
    }
}
