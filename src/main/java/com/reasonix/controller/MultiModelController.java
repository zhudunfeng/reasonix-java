package com.reasonix.controller;

import com.reasonix.agent.AgentController;
import com.reasonix.config.ReasonixConfig;
import com.reasonix.provider.ModelFactory;
import com.reasonix.provider.ModelRegistry;
import com.reasonix.provider.ModelResolver;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 多模型控制器。
 */
@RestController
@RequestMapping("/api/chat/multi")
public class MultiModelController {

    private final AgentController agentController;
    private final ModelFactory modelFactory;
    private final ModelRegistry modelRegistry;
    private final ReasonixConfig reasonixConfig;

    public MultiModelController(AgentController agentController,
                                ModelFactory modelFactory,
                                ModelRegistry modelRegistry,
                                ReasonixConfig reasonixConfig) {
        this.agentController = agentController;
        this.modelFactory = modelFactory;
        this.modelRegistry = modelRegistry;
        this.reasonixConfig = reasonixConfig;
    }

    @PostMapping
    public Map<String, Object> multi(@RequestBody Map<String, Object> body) {
        String query = (String) body.get("query");
        List<String> modelIds = (List<String>) body.get("modelIds");
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        if (modelIds != null) {
            for (String modelId : modelIds) {
                // 解析 modelId：防止传入 "supplierId-modelName" 拼接格式
                String resolved = ModelResolver.resolve(modelId, modelRegistry, reasonixConfig);
                result.put(modelId, agentController.execute(query, "default", resolved));
            }
        }
        return result;
    }
}
