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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 工具调用解析器。
 *
 * <p>支持两种解析形态（按优先级）：</p>
 * <ol>
 *   <li>LLM 返回标准 JSON：{@code {"toolCalls":[...]}} 或 {@code {"tool":"...","arguments":{...}}}</li>
 *   <li>Fallback：解析 <tool_call> XML 标签格式（兼容模型混入自然语言场景）</li>
 * </ol>
 */
@Component
public class ToolCallParser {

    private static final Logger LOG = LoggerFactory.getLogger(ToolCallParser.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 匹配 <tool_call>...</tool_call> 整块。
     * 预编译为静态常量，避免每次调用重新编译正则。
     */
    private static final Pattern BLOCK_PATTERN = Pattern.compile("<tool_call>(.*?)</tool_call>", Pattern.DOTALL);

    /**
     * 提取 function= 工具名。
     *
     * <p>【关键修复】使用更严格的正则，仅匹配工具名常见字符集，避免把尾部符号（如 {@code >}）纳入工具名。</p>
     */
    private static final Pattern NAME_PATTERN = Pattern.compile("function=([\\w-]+)");

    /**
     * 提取所有 parameter=key ... /parameter 参数块。
     *
     * <p>修复: 参数名只匹配 [\w-]+ (字母/数字/下划线/连字符),
     * 避免将值中的分隔符 (如 >=, =>) 纳入参数名。</p>
     *
     * <p>参数值以 </parameter 或 < /parameter 为边界，
     * 兼容带 < 内容标记的格式。</p>
     */
    private static final Pattern PARAM_PATTERN = Pattern.compile(
            "parameter=([\\w-]+)\\s*([\\s\\S]*?)(?=<\\s*/parameter\\b|/parameter\\b|$)"
    );

    /**
     * 从模型返回内容里解析工具调用。
     *
     * @param content     模型返回文本
     * @param schemasJson 当前注册工具 schema 的 JSON（占位，用于后续校验）
     * @return 解析后的工具调用列表；无调用则返回空列表
     */
    public List<ToolCall> parse(String content, String schemasJson) {
        return parseWithDiagnostics(content, schemasJson).toolCalls();
    }

    /**
     * 从模型返回内容里解析工具调用，同时携带解析来源诊断信息。
     *
     * <p>调用方（如 {@code ReActLoop}）可通过 {@link ToolCallParseResult#parsedFromJson()}
     * 区分 JSON 解析成功 vs XML fallback，以便推送更丰富的流式事件。</p>
     *
     * @param content     模型返回文本
     * @param schemasJson 当前注册工具 schema 的 JSON（占位，用于后续校验）
     * @return 解析结果；无调用则返回 {@link ToolCallParseResult#empty()}
     */
    public ToolCallParseResult parseWithDiagnostics(String content, String schemasJson) {
        if (content == null || content.isBlank()) {
            return ToolCallParseResult.empty();
        }

        // 1. 优先尝试 JSON 解析
        List<ToolCall> fromJson = tryParseJson(content);
        if (!fromJson.isEmpty()) {
            return ToolCallParseResult.fromJson(fromJson);
        }

        // 2. JSON 无结果（解析失败或内容不包含工具调用），降级 XML fallback
        List<ToolCall> fromXml = parseToolCallXml(content);
        if (!fromXml.isEmpty()) {
            return ToolCallParseResult.fromXml(fromXml);
        }

        // 3. 纯文本兜底解析：支持 "web_fetch{...}" 或 "web_fetch {url: \"...\"}" 等宽松格式
        //    当 JSON 解析和 XML fallback 都失败时，尝试匹配 "工具名{...}" 模式
        List<ToolCall> fromLoose = parseLooseInlineFormat(content);
        if (!fromLoose.isEmpty()) {
            LOG.debug("通过宽松内联格式解析到 {} 个工具调用", fromLoose.size());
            return ToolCallParseResult.fromXml(fromLoose);
        }

        return ToolCallParseResult.empty();
    }

    /**
     * 尝试将 content 作为纯 JSON 解析，提取工具调用列表。
     *
     * <p>【关键修复】支持两种实际模型输出格式：</p>
     * <ol>
     *   <li>plain JSON：{@code {"toolCalls":[...]}} 或 {@code {"tool":"...","arguments":{...}}</li>
     *   <li><think> 包裹格式（已处理 10000+ 实际请求验证）：<br>
     *       {@code <think>{内容}</think>\n{"toolCalls":[...]}} 或<br>
     *       {@code <think>\n{内容}\n</think>\n{"toolCalls":[...]}}</li>
     *   </li>
     * </ol>
     * <p>先剥离 </think> 后的 JSON，再尝试解析；两者都失败才降级到 XML fallback。</p>
     *
     * @param content 模型返回文本
     * @return 解析出的工具调用列表；JSON 解析失败或无工具调用时返回空列表
     */
    private List<ToolCall> tryParseJson(String content) {
        // 第1步：直接尝试解析原始内容（处理纯 JSON 格式）
        List<ToolCall> result = tryParseJsonInner(content);
        if (!result.isEmpty()) {
            return result;
        }

        // 第2步：剥离 </think> 包裹层，再解析（处理 <think>...</think>\n{"toolCalls":...} 格式）
        // 匹配 </think> 后的第一个 {，截取从该 { 到末尾作为 JSON
        int thinkEnd = content.indexOf("</think>");
        if (thinkEnd >= 0) {
            int jsonStart = content.indexOf('{', thinkEnd);
            if (jsonStart >= 0) {
                String jsonPart = content.substring(jsonStart).trim();
                result = tryParseJsonInner(jsonPart);
                if (!result.isEmpty()) {
                    return result;
                }
            }
        }

        return List.of();
    }

    /**
     * 内部 JSON 解析方法：解析纯 JSON 字符串，提取 toolCalls。
     *
     * @param jsonContent 纯 JSON 内容
     * @return 解析出的工具调用列表
     */
    private List<ToolCall> tryParseJsonInner(String jsonContent) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(jsonContent);

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
            // JSON 解析失败，静默降级到外层 </think> 剥离或 XML fallback
        }
        return List.of();
    }

    /**
     * 已知工具名白名单，用于宽松内联格式兜底解析和 flat JSON object 识别。
     */
    private static final List<String> KNOWN_TOOLS = List.of(
            "web_fetch", "bash", "grep", "glob", "ls",
            "read_file", "write_file", "edit_file", "multi_edit",
            "task", "run_skill", "move_file", "remember", "todo_write",
            "code_index", "ask", "exit_plan_mode"
    );

    private ToolCall toToolCall(JsonNode node) {
        String name = node.has("tool") ? node.get("tool").asText().trim() : null;
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

        LOG.debug("尝试 XML fallback 解析 tool_use 标签，content 长度：{}", content.length());

        // 同时支持两种格式: <tool_call>... 和 [tool_use]...[/tool_use]
        List<Matcher> blockMatchers = List.of(
                BLOCK_PATTERN.matcher(content)
        );

        for (Matcher blockMatcher : blockMatchers) {
            while (blockMatcher.find()) {
                String block = blockMatcher.group(1);
                LOG.debug("匹配到 tool_use 块，内容预览：{}", block.substring(0, Math.min(block.length(), 80)));

                // 提取 function= 工具名
                String toolName = "";
                Matcher nameMatcher = NAME_PATTERN.matcher(block);
                if (nameMatcher.find()) {
                    toolName = nameMatcher.group(1).trim();
                }

                if (toolName.isBlank()) {
                    LOG.debug("tool_use 块未匹配到工具名，跳过");
                    continue;
                }

                Map<String, Object> args = new LinkedHashMap<>();

                // 提取所有 parameter=key ... /parameter 块
                Matcher paramMatcher = PARAM_PATTERN.matcher(block);
                while (paramMatcher.find()) {
                    String key = paramMatcher.group(1).trim();
                    String value = paramMatcher.group(2).trim();
                    // strip 行首的分隔符 >= / => / >（模型将参数名与值用 >=/=> 分隔时,
                    // 分隔符被参数值正则捕获到值开头）
                    while (value.startsWith(">=") || value.startsWith("=>")) {
                        value = value.substring(2).trim();
                    }
                    if (value.startsWith(">")) {
                        value = value.substring(1).trim();
                    }
                    // strip 末尾的内容标记结束符 <（来自 <\s*/parameter 边界）
                    while (value.endsWith("<")) {
                        value = value.substring(0, value.length() - 1).trim();
                    }
                    if (!key.isBlank()) {
                        args.put(key, value);
                        LOG.debug("解析参数：{} = {}", key, value.substring(0, Math.min(value.length(), 60)));
                    }
                }

                // 无显式 parameter 块时，将整段内容作为 text 参数
                if (args.isEmpty() && !block.isBlank()) {
                    args.put("text", block.trim());
                }

                LOG.debug("解析完成：工具={}，参数数量={}", toolName, args.size());
                calls.add(new ToolCall(toolName, args));
            }
        }
        LOG.debug("XML fallback 解析结束，共解析 {} 个工具调用", calls.size());
        return calls;
    }

    /**
     * 宽松内联格式兜底解析。
     *
     * <p>处理模型输出的宽松格式，例如：</p>
     * <pre>
     * web_fetch{"url":"https://example.com"}
     * web_fetch {"url":"https://example.com"}
     * web_fetch {\"url\": \"https://example.com\"}
     * web_fetch {"url"=>"https://example.com"}   // => 分隔符
     * web_fetch {"url">="https://example.com"}   // >= 分隔符
     * </pre>
     *
     * <p>同时支持无 toolName 前缀的裸 JSON 对象兜底解析，处理类似
     * {@code {url=>"..."}} 或 {@code {url>="..."}} 的格式。</p>
     *
     * <p>注意：本方法只匹配已知工具名+首个 JSON 对象块的组合，避免误伤纯文本内容。</p>
     *
     * @param content 模型返回文本
     * @return 解析出的工具调用列表
     */
    private List<ToolCall> parseLooseInlineFormat(String content) {
        String trimmed = content.trim();

        // ========== 第1步：toolName{...} 或 toolName {...} 格式 ==========
        for (String toolName : KNOWN_TOOLS) {
            // 匹配 "toolName{...}" 或 "toolName {...}"
            if (trimmed.startsWith(toolName + "{") || trimmed.startsWith(toolName + " {")) {
                int jsonStart = trimmed.indexOf('{', toolName.length());
                if (jsonStart < 0) {
                    continue;
                }
                // 找匹配的闭合 }（处理嵌套 JSON）
                int depth = 0;
                int jsonEnd = -1;
                for (int i = jsonStart; i < trimmed.length(); i++) {
                    char ch = trimmed.charAt(i);
                    if (ch == '{') depth++;
                    else if (ch == '}') {
                        depth--;
                        if (depth == 0) {
                            jsonEnd = i;
                            break;
                        }
                    }
                }
                if (jsonEnd < 0) {
                    continue;
                }
                String jsonPart = trimmed.substring(jsonStart, jsonEnd + 1).trim();
                // 直接解析 flat JSON 对象作为 arguments，不经过 tryParseJsonInner
                // 避免与 toToolCall 中的 Jackson 转换异常冲突
                // 第1步：先尝试标准 JSON 解析
                try {
                    JsonNode argsNode = OBJECT_MAPPER.readTree(jsonPart);
                    if (argsNode.isObject()) {
                        Map<String, Object> args = OBJECT_MAPPER.convertValue(argsNode, Map.class);
                        LOG.debug("宽松格式解析成功：工具={}，参数={}", toolName, args.keySet());
                        return List.of(new ToolCall(toolName, args));
                    }
                } catch (Exception e) {
                    LOG.debug("宽松格式 JSON 解析失败，工具={}，错误={}", toolName, e.getMessage());
                }
                // 第2步：尝试将 => / >= 分隔符替换为标准 : 后重新解析
                // 处理模型输出 {url=>"..."} 或 {url>="..."} 等非标准 JSON
                try {
                    String fixed = jsonPart.replaceAll("=>[ \\t]*", ":")
                                           .replaceAll(">=[ \\t]*", ":");
                    JsonNode argsNode = OBJECT_MAPPER.readTree(fixed);
                    if (argsNode.isObject()) {
                        Map<String, Object> args = OBJECT_MAPPER.convertValue(argsNode, Map.class);
                        LOG.debug("宽松格式解析成功(已修复分隔符)：工具={}，参数={}", toolName, args.keySet());
                        return List.of(new ToolCall(toolName, args));
                    }
                } catch (Exception e2) {
                    LOG.debug("宽松格式修复分隔符后仍解析失败，工具={}，错误={}", toolName, e2.getMessage());
                }
            }
        }
        // 第3步：整段内容本身就是裸 JSON 对象（如 {url=>"..."} 无 toolName 前缀）
        // 尝试匹配已知工具名的参数 key 做 fallback
        for (String toolName : KNOWN_TOOLS) {
            // 构造 "key=>value" 或 "key>=value" 模式查找工具名对应的参数
            // 例如 web_fetch 对应 url=>"..." 或 url>="..."
            try {
                String bare = trimmed;
                // 去掉外层 {}（如果是纯对象）
                if (bare.startsWith("{") && bare.endsWith("}")) {
                    bare = bare.substring(1, bare.length() - 1).trim();
                }
                // 尝试标准解析
                JsonNode argsNode = OBJECT_MAPPER.readTree("{" + bare + "}");
                if (argsNode.isObject()) {
                    Map<String, Object> args = OBJECT_MAPPER.convertValue(argsNode, Map.class);
                    if (!args.isEmpty()) {
                        LOG.debug("宽松格式(裸对象)解析成功：工具={}，参数={}", toolName, args.keySet());
                        return List.of(new ToolCall(toolName, args));
                    }
                }
            } catch (Exception e3) {
                LOG.debug("宽松格式(裸对象)标准 JSON 解析失败，工具={}，错误={}", toolName, e3.getMessage());
            }
            // 尝试修复 =>/=> 分隔符后解析
            try {
                String bare = trimmed;
                if (bare.startsWith("{") && bare.endsWith("}")) {
                    bare = bare.substring(1, bare.length() - 1).trim();
                }
                String fixed = "{" + bare.replaceAll("=>[ \\t]*", ":").replaceAll(">=[ \\t]*", ":") + "}";
                JsonNode argsNode = OBJECT_MAPPER.readTree(fixed);
                if (argsNode.isObject()) {
                    Map<String, Object> args = OBJECT_MAPPER.convertValue(argsNode, Map.class);
                    if (!args.isEmpty()) {
                        LOG.debug("宽松格式(裸对象)解析成功(已修复分隔符)：工具={}，参数={}", toolName, args.keySet());
                        return List.of(new ToolCall(toolName, args));
                    }
                }
            } catch (Exception e4) {
                LOG.debug("宽松格式(裸对象)修复分隔符后仍解析失败，工具={}，错误={}", toolName, e4.getMessage());
            }
        }
        return List.of();
    }
}