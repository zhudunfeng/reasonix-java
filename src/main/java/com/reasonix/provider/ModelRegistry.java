package com.reasonix.provider;

import com.reasonix.config.ReasonixConfig;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 模型注册表。
 *
 * <p>维护 supplier + model 两层注册信息，并提供运行时查询能力。
 */
@Component
public class ModelRegistry {

    private final Map<String, ReasonixConfig.ProviderProperties.ModelDef> models = new LinkedHashMap<>();
    private final Map<String, ReasonixConfig.ProviderProperties.SupplierDef> suppliers = new LinkedHashMap<>();

    public void register(ReasonixConfig.ProviderProperties.SupplierDef supplier) {
        if (supplier == null || supplier.getId() == null) {
            return;
        }
        suppliers.put(supplier.getId(), supplier);
    }

    public void register(ReasonixConfig.ProviderProperties.ModelDef model) {
        if (model == null || model.getId() == null) {
            return;
        }
        models.put(model.getId(), model);
    }

    public Optional<ReasonixConfig.ProviderProperties.ModelDef> getModel(String modelId) {
        return Optional.ofNullable(models.get(modelId));
    }

    public List<String> getModelIds() {
        return new ArrayList<>(models.keySet());
    }

    public Optional<ReasonixConfig.ProviderProperties.SupplierDef> getSupplier(String supplierId) {
        return Optional.ofNullable(suppliers.get(supplierId));
    }

    public List<String> getSupplierIds() {
        return new ArrayList<>(suppliers.keySet());
    }
}
