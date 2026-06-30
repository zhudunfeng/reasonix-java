package com.reasonix.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 工具调用解析器。
 *
 * <p>支持两种解析形态（按优先级）：</p>
 * <ol>
 *   <li>LLM 返回标准 JSON：{@code {"toolCalls":[...]}} 或 {@code {"tool":"...","arguments":{...}}}</li>
 *   <li>Fallback：解析 tool_use XML 标签格式（兼容模型混入自然语言场景）</li>
 * </ol>
 */
@Component
public class ToolCallParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 从模型返回内容里解析工具调用。
     *
     * @param content     模型返回文本
     * @param schemasJson 当前注册工具 schema 的 JSON（占位，用于后续校验）
     * @return 解析后的工具调用列表；无调用则返回空列表
     */
    public List<ToolCall> parse(String content, String schemasJson) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        // 1. 优先尝试 JSON 解析
        List<ToolCall> fromJson = tryParseJson(content);
        if (!fromJson.isEmpty()) {
            return fromJson;
        }

        // 2. JSON 无结果（解析失败或内容不包含工具调用），降级 XML fallback
        List<ToolCall> fromXml = parseToolCallXml(content);
        if (!fromXml.isEmpty()) {
            return fromXml;
        }

        return List.of();
    }

    /**
     * 尝试将 content 作为纯 JSON 解析，提取工具调用列表。
     *
     * @param content 模型返回文本
     * @return 解析出的工具调用列表；JSON 解析失败或无工具调用时返回空列表
     */
    private List<ToolCall> tryParseJson(String content) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(content);

            // 形态 1：{"toolCalls":[{"tool":"...","arguments":{...}}, ...]}
            if (root.has("toolCalls") && root.get("toolCalls").isArray()) {
                List<ToolCall> calls = new ArrayList<>();
                for (JsonNode node : root.get("toolCalls")) {
                    ToolCall call = toToolCall(node);
                    if (call != null) {
                        calls.add(call);
                    }
                }
                if (!calls.isEmpty()) {
                    return calls;
                }
            }

            // 形态 2：{"tool":"...","arguments":{...}}
            if (root.has("tool") && root.has("arguments")) {
                ToolCall call = toToolCall(root);
                if (call != null) {
                    return List.of(call);
                }
            }
        } catch (Exception ignored) {
            // JSON 解析失败，静默降级到 XML fallback
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
                // 参数解析失败则使用空参数继续，不阻塞主流程
            }
        }
        if (name == null || name.isBlank()) {
            return null;
        }
        return new ToolCall(name, args);
    }

    /**
     * Fallback：解析模型混入自然语言的 tool_use XML 工具调用格式。
     *
     * <p>当模型未遵循 JSON 输出指令，将 tool_use 标签与自然语言混在一起输出时，
     * 本方法通过正则提取工具名和参数，保证工具调用不丢失。</p>
     *
     * <p>示例：</p>
     * <pre>
     * 我来帮您查询济南天气。
     * [tool_use]
     * function=web_fetch
     * parameter=url
     * http://www.weather.com.cn/weather1d/101120101.shtml
     * /parameter
     * /function
     * [/tool_use]
     * </pre>
     *
     * @param content 模型返回文本（含 tool_use 标签）
     * @return 解析出的工具调用列表；无匹配则返回空列表
     */
    private List<ToolCall> parseToolCallXml(String content) {
        List<ToolCall> calls = new ArrayList<>();

        // 匹配 [tool_use] ... [/tool_use] 整块
        Pattern blockPattern = Pattern.compile("\\[tool_use\\](.*?)\\[/tool_use\\]", Pattern.DOTALL);
        Matcher blockMatcher = blockPattern.matcher(content);
        while (blockMatcher.find()) {
            String block = blockMatcher.group(1);

            // 提取 function= 工具名
            String toolName = "";
            Pattern namePattern = Pattern.compile("function=(\\S+)");
            Matcher nameMatcher = namePattern.matcher(block);
            if (nameMatcher.find()) {
                toolName = nameMatcher.group(1).trim();
            }

            if (toolName.isBlank()) {
                continue;
            }

            Map<String, Object> args = new LinkedHashMap<>();

            // 提取所有 parameter=key ... /parameter 块
            Pattern paramPattern = Pattern.compile("parameter=(\\S+)\\s*(.*?)(?:/parameter|$)", Pattern.DOTALL);
            Matcher paramMatcher = paramPattern.matcher(block);
            while (paramMatcher.find()) {
                String key = paramMatcher.group(1).trim();
                String value = paramMatcher.group(2).trim();
                if (!key.isBlank()) {
                    args.put(key, value);
                }
            }

            // 无显式 parameter 块时，将整段内容作为 text 参数
            if (args.isEmpty() && !block.isBlank()) {
                args.put("text", block.trim());
            }

            calls.add(new ToolCall(toolName, args));
        }
        return calls;
    }
}