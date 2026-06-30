package com.reasonix.provider;

import com.reasonix.config.ReasonixConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provider 层配置。
 *
 * <p>负责将 ModelFactory 生产的模型实例注册为 Spring Bean，供 ReActLoop 等组件注入使用。
 */
@Configuration
public class ProviderConfig {

    private final ModelFactory modelFactory;
    private final ModelRegistry modelRegistry;
    private final ReasonixConfig reasonixConfig;

    public ProviderConfig(ModelFactory modelFactory, ModelRegistry modelRegistry, ReasonixConfig reasonixConfig) {
        this.modelFactory = modelFactory;
        this.modelRegistry = modelRegistry;
        this.reasonixConfig = reasonixConfig;
    }

    /**
     * 默认 ChatModel Bean。
     *
     * <p>使用配置中的 default-model 创建，保证 ReActLoop 等组件可以正常注入。
     */
    @Bean
    public ChatModel chatModel() {
        String defaultModel = resolveDefaultModel();
        if (defaultModel == null || defaultModel.isBlank()) {
            defaultModel = "default";
        }

        // 确保默认模型及供应商信息已注册到 ModelRegistry，避免启动时序导致找不到定义
        if (modelRegistry.getModel(defaultModel).isEmpty() && reasonixConfig.getProvider() != null) {
            reasonixConfig.getProvider().getModels().forEach(modelRegistry::register);
            reasonixConfig.getProvider().getSuppliers().forEach(modelRegistry::register);
        }

        // 智能 modelId 解析：若 modelId 包含 '-' 且注册表中找不到，尝试按
        // "supplierId-modelName" 格式拆分，自动映射到注册表里定义的 model id。
        // 例如 stepfun-3.7-flash → 找到 supplier stepfun 下 modelName=step-3.7-flash → 解析为 step-3.7-flash
        if (modelRegistry.getModel(defaultModel).isEmpty() && reasonixConfig.getProvider() != null) {
            String resolved = tryResolveModelId(defaultModel, reasonixConfig.getProvider().getModels(),
                    reasonixConfig.getProvider().getSuppliers());
            if (resolved != null) {
                defaultModel = resolved;
            }
        }

        ChatModel chatModel = modelFactory.createChatModel(defaultModel);
        System.out.println("[Reasonix] chatModel instance=" + chatModel
                + ", class=" + chatModel.getClass().getName());
        return chatModel;
    }

    /**
     * 解析 "supplierId-modelName" 拼接格式的 modelId。
     *
     * <p>当前端误将 supplier-id 和 model-name 拼接后传入（如 stepfun-3.7-flash），
     * 本方法按 supplierId + modelName 格式拆分，在配置的 model 列表中查找匹配项，
     * 并返回注册表里正确的 modelId（如 step-3.7-flash）。</p>
     */
    private static String tryResolveModelId(
            String modelId,
            java.util.List<ReasonixConfig.ProviderProperties.ModelDef> models,
            java.util.List<ReasonixConfig.ProviderProperties.SupplierDef> suppliers) {
        if (modelId == null || !modelId.contains("-")) {
            return null;
        }

        // 找到 modelId 中第一个 '-' 前面是否匹配某个 supplier id
        for (ReasonixConfig.ProviderProperties.SupplierDef supplier : suppliers) {
            if (supplier.getId() == null) continue;
            String supplierId = supplier.getId();
            if (modelId.startsWith(supplierId + "-")) {
                // 取 supplier id 后面的部分作为 modelName
                String candidateName = modelId.substring(supplierId.length() + 1);
                // 在 model 列表中查找 supplier-id 匹配且 model-name 匹配的条目
                for (ReasonixConfig.ProviderProperties.ModelDef model : models) {
                    if (supplierId.equals(model.getSupplierId())
                            && candidateName.equals(model.getModelName())) {
                        return model.getId();
                    }
                }
            }
        }
        return null;
    }

    private String resolveDefaultModel() {
        if (reasonixConfig.getProvider() != null
                && reasonixConfig.getProvider().getDefaultModel() != null
                && !reasonixConfig.getProvider().getDefaultModel().isBlank()) {
            return reasonixConfig.getProvider().getDefaultModel();
        }
        return reasonixConfig.getDefaultModel();
    }
}
