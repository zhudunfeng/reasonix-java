package com.reansonix.controller;

import com.reansonix.config.ReasonixConfig;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 模型管理控制器 - 提供模型与供应商信息查询接口。
 */
@RestController
@RequestMapping("/api/models")
public class ModelAdminController {

    private final ReasonixConfig reasonixConfig;

    public ModelAdminController(ReasonixConfig reasonixConfig) {
        this.reasonixConfig = reasonixConfig;
    }

    /**
     * 获取默认模型配置。
     *
     * @return 默认模型 ID
     */
    @GetMapping("/default")
    public Map<String, String> defaultModel() {
        return Map.of("defaultModel", reasonixConfig.getDefaultModel());
    }

    /**
     * 获取供应商列表。
     *
     * @return 供应商列表
     */
    @GetMapping("/suppliers")
    public List<Map<String, Object>> suppliers() {
        return reasonixConfig.getProvider().getSuppliers().stream()
                .map(this::toSupplierMap)
                .toList();
    }

    /**
     * 获取模型列表。
     *
     * @return 模型列表
     */
    @GetMapping("/list")
    public List<Map<String, Object>> models() {
        return reasonixConfig.getProvider().getModels().stream()
                .map(this::toModelMap)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toSupplierMap(ReasonixConfig.ProviderProperties.SupplierDef supplier) {
        return Map.of(
                "id", supplier.getId(),
                "providerType", supplier.getProviderType(),
                "baseUrl", supplier.getBaseUrl(),
                "enabled", supplier.isEnabled()
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toModelMap(ReasonixConfig.ProviderProperties.ModelDef model) {
        return Map.of(
                "id", model.getId(),
                "supplierId", model.getSupplierId(),
                "modelName", model.getModelName(),
                "stream", model.isStream(),
                "enabled", model.isEnabled()
        );
    }
}
