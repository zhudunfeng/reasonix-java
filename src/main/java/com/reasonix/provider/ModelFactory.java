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
    private final ReasonixConfig reasonixConfig;

    public ModelFactory(ModelRegistry modelRegistry, ReasonixConfig reasonixConfig) {
        this.modelRegistry = modelRegistry;
        this.reasonixConfig = reasonixConfig;
    }

    /**
     * 根据模型 ID 创建 {@link ChatModel}。
     */
    public ChatModel createChatModel(String modelId) {
        ReasonixConfig.ProviderProperties.ModelDef modelDef = modelRegistry.getModel(modelId)
                .orElseThrow(() -> new IllegalArgumentException("未找到模型定义: " + modelId));

        ReasonixConfig.ProviderProperties.SupplierDef supplierDef = modelRegistry.getSupplier(modelDef.getSupplierId())
                .orElseThrow(() -> new IllegalArgumentException("未找到供应商定义: " + modelDef.getSupplierId()));

        return new OpenAiCompatibleChatModel(
                modelId,
                modelDef.getModelName(),
                supplierDef.getBaseUrl(),
                supplierDef.getApiKey()
        );
    }

    /**
     * 根据模型 ID 创建 {@link EmbeddingModel}。
     */
    public EmbeddingModel createEmbeddingModel(String modelId) {
        // 占位：返回内存 Embedding 实现
        return new InMemoryEmbeddingModel();
    }
}
