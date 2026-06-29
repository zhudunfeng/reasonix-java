package com.reasonix.provider;

import com.reasonix.config.ReasonixConfig;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 模型注册初始化器。
 *
 * <p>应用启动时读取 YAML 配置并注册到 ModelRegistry。
 */
@Component
public class ModelRegistrationInitializer {

    private final ModelRegistry modelRegistry;
    private final ReasonixConfig reasonixConfig;

    public ModelRegistrationInitializer(ModelRegistry modelRegistry, ReasonixConfig reasonixConfig) {
        this.modelRegistry = modelRegistry;
        this.reasonixConfig = reasonixConfig;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        if (reasonixConfig.getProvider() == null) {
            return;
        }
        reasonixConfig.getProvider().getSuppliers().forEach(modelRegistry::register);
        reasonixConfig.getProvider().getModels().forEach(modelRegistry::register);
    }
}
