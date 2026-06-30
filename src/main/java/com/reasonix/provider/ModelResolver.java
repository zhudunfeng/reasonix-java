package com.reasonix.provider;

import com.reasonix.config.ReasonixConfig;
import java.util.List;

/**
 * 模型 ID 解析器。
 *
 * <p>负责将前端或外部传入的 modelId 解析为注册表中实际存在的 modelId。
 * 当传入的 modelId 格式为 "supplierId-modelName"（如 stepfun-3.7-flash）时，
 * 自动映射到配置中定义的 model id（如 step-3.7-flash）。</p>
 */
public final class ModelResolver {

    private ModelResolver() {
    }

    /**
     * 解析 modelId，如果注册表中找不到，尝试按 supplierId-modelName 格式拆分映射。
     *
     * @param modelId      传入的 modelId（可能为拼接格式）
     * @param modelRegistry 模型注册表
     * @param config        Reasonix 配置
     * @return 注册表中实际存在的 modelId，无法解析则返回原值
     */
    public static String resolve(String modelId, ModelRegistry modelRegistry, ReasonixConfig config) {
        if (modelId == null || modelId.isBlank()) {
            return modelId;
        }
        // 直接命中注册表
        if (modelRegistry.getModel(modelId).isPresent()) {
            return modelId;
        }
        // 尝试按 "supplierId-modelName" 格式拆分映射
        if (config.getProvider() != null) {
            List<ReasonixConfig.ProviderProperties.SupplierDef> suppliers = config.getProvider().getSuppliers();
            List<ReasonixConfig.ProviderProperties.ModelDef> models = config.getProvider().getModels();
            if (suppliers != null && models != null) {
                String resolved = tryResolveBySupplierAndName(modelId, models, suppliers);
                if (resolved != null) {
                    System.out.println("[ModelResolver] modelId=" + modelId + " 已映射为 " + resolved);
                    return resolved;
                }
            }
        }
        return modelId;
    }

    /**
     * 按 "supplierId-modelName" 格式拆分 modelId 并查找注册表中对应的实际 modelId。
     *
     * <p>例如：传入 "stepfun-3.7-flash" → 匹配 supplier "stepfun" 的 modelName "3.7-flash"
     * → 返回注册表中 model id "step-3.7-flash"。</p>
     */
    private static String tryResolveBySupplierAndName(
            String modelId,
            List<ReasonixConfig.ProviderProperties.ModelDef> models,
            List<ReasonixConfig.ProviderProperties.SupplierDef> suppliers) {
        if (modelId == null || !modelId.contains("-")) {
            return null;
        }
        for (ReasonixConfig.ProviderProperties.SupplierDef supplier : suppliers) {
            if (supplier.getId() == null) continue;
            String supplierId = supplier.getId();
            if (modelId.startsWith(supplierId + "-")) {
                String candidateName = modelId.substring(supplierId.length() + 1);
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
}
