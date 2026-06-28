package com.reansonix.controller;

import com.reansonix.agent.AgentController;
import com.reansonix.provider.ChatModel;
import com.reansonix.provider.ModelFactory;
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

    public MultiModelController(AgentController agentController, ModelFactory modelFactory) {
        this.agentController = agentController;
        this.modelFactory = modelFactory;
    }

    @PostMapping
    public Map<String, Object> multi(@RequestBody Map<String, Object> body) {
        String query = (String) body.get("query");
        List<String> modelIds = (List<String>) body.get("modelIds");
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        if (modelIds != null) {
            for (String modelId : modelIds) {
                result.put(modelId, agentController.execute(query, "default", modelId));
            }
        }
        return result;
    }
}
