package com.reansonix.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 最小可用工具调用解析器。
 *
 * <p>支持两种形态：
 * 1. LLM 返回中携带简单 JSON 工具调用块：{"tool":"write_file","arguments":{...}}
 * 2. 连续返回多段调用：{"toolCalls":[{"tool":"write_file","arguments":{...}}]}
 *
 * <p>由于当前项目为占位实现，这里做最小解析；后续可升级为严格 schema 校验。
 */
@Component
public class ToolCallParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 从模型返回内容里解析工具调用。
     *
     * @param content 模型返回文本
     * @param schemasJson 当前注册工具 schema 的 JSON（占位，用于后续校验）
     * @return 解析后的工具调用列表；失败返回空列表
     */
    public List<ToolCall> parse(String content, String schemasJson) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(content);
            if (root.has("toolCalls") && root.get("toolCalls").isArray()) {
                List<ToolCall> calls = new ArrayList<>();
                for (JsonNode node : root.get("toolCalls")) {
                    ToolCall call = toToolCall(node);
                    if (call != null) {
                        calls.add(call);
                    }
                }
                return calls;
            }

            if (root.has("tool") && root.has("arguments")) {
                ToolCall call = toToolCall(root);
                if (call != null) {
                    return List.of(call);
                }
            }
        } catch (Exception ignored) {
            // 最小实现：解析失败则返回空，避免阻塞主流程
        }

        return List.of();
    }

    private ToolCall toToolCall(JsonNode node) {
        String name = node.has("tool") ? node.get("tool").asText() : null;
        JsonNode argsNode = node.has("arguments") ? node.get("arguments") : null;
        Map<String, Object> args = Map.of();
        if (argsNode != null && argsNode.isObject()) {
            try {
                args = OBJECT_MAPPER.convertValue(argsNode, Map.class);
            } catch (Exception ignored) {
                // 最小实现：参数解析失败则使用空参数继续
            }
        }
        if (name == null || name.isBlank()) {
            return null;
        }
        return new ToolCall(name, args);
    }
}
