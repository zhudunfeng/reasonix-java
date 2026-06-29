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

        // 确保默认模型已注册到 ModelRegistry，避免启动时序导致找不到模型定义
        if (modelRegistry.getModel(defaultModel).isEmpty() && reasonixConfig.getProvider() != null) {
            reasonixConfig.getProvider().getModels().forEach(modelRegistry::register);
        }

        try {
            return modelFactory.createChatModel(defaultModel);
        } catch (IllegalArgumentException ex) {
            // 配置中未找到模型定义时，回退到最小占位实现，避免启动失败
            return new OpenAiCompatibleChatModel(defaultModel);
        }
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
