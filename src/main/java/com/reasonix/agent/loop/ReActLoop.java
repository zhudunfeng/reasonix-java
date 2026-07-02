package com.reasonix.agent.loop;

import com.reasonix.agent.StreamingEvent;
import com.reasonix.agent.StreamingEventListener;
import com.reasonix.agent.StreamingEventType;
import com.reasonix.agent.compact.CompactService;
import com.reasonix.agent.model.Session;
import com.reasonix.agent.store.SessionStore;
import com.reasonix.config.ReasonixConfig;
import com.reasonix.provider.ChatModel;
import com.reasonix.provider.ChatMessage;
import com.reasonix.provider.ChatRequest;
import com.reasonix.provider.ChatResponse;
import com.reasonix.tool.PermissionGate;
import com.reasonix.tool.Tool;
import com.reasonix.tool.ToolCall;
import com.reasonix.tool.ToolCallParser;
import com.reasonix.tool.ToolContext;
import com.reasonix.tool.ToolExecutionResult;
import com.reasonix.tool.ToolRegistry;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * ReAct 核心执行循环。
 *
 * <p>核心流程：reasoning(model call) → acting(parse → execute tools → append results) → check finish
 * → compact/summary fallback。每轮都基于会话上下文构建 prompt，而不是把系统提示永久混入历史。</p>
 *
 * <p>参考实现补充的能力：</p>
 * <ul>
 *   <li>显式 reasoning / acting / summary 阶段划分</li>
 *   <li>pending tool 恢复：若上一轮仍有未返回结果的工具调用，先恢复执行而不是重新推理</li>
 *   <li>更强的结果校验：unknown / permission denied 以结构化信息写入历史</li>
 *   <li>超出最大步数后进入 summary 兜底，并尽量返回可读答案</li>
 *   <li>工具调用标记清洗，避免把原始标记暴露给用户</li>
 * </ul>
 */
@Component
public class ReActLoop {

    private final ChatModel chatModel;
    private final SessionStore sessionStore;
    private final ReasonixConfig reasonixConfig;
    private final ToolRegistry toolRegistry;
    private final ToolCallParser toolCallParser;
    private final PermissionGate permissionGate;
    private final CompactService compactService = new CompactService();

    public ReActLoop(ChatModel chatModel,
                     SessionStore sessionStore,
                     ReasonixConfig reasonixConfig,
                     ToolRegistry toolRegistry,
                     ToolCallParser toolCallParser,
                     PermissionGate permissionGate) {
        this.chatModel = chatModel;
        this.sessionStore = sessionStore;
        this.reasonixConfig = reasonixConfig;
        this.toolRegistry = toolRegistry;
        this.toolCallParser = toolCallParser;
        this.permissionGate = permissionGate;
    }

    /**
     * 执行一次 ReAct 循环。
     *
     * @param sessionId 会话ID
     * @param query 用户查询
     * @return 最终响应内容
     */
    public String execute(String sessionId, String query) {
        return execute(sessionId, query, null);
    }

    /**
     * 执行一次 ReAct 循环，并可选推送流式事件。
     */
    public String execute(String sessionId, String query, StreamingEventListener listener) {
        Session session = sessionStore.get(sessionId).orElseGet(() -> sessionStore.create(sessionId));
        session.getHistory().add(new com.reasonix.agent.model.ChatMessage(com.reasonix.agent.model.ChatMessage.Role.USER, query));

        int maxSteps = safeMaxSteps();
        String toolSchemasJson = toolRegistry.getToolSchemasJson();

        String finalAnswer = null;

        // 若存在上一轮遗留的工具调用，优先恢复执行工具阶段
        if (hasPendingToolCalls(session)) {
            if (listener != null) {
                listener.onEvent(StreamingEvent.builder(StreamingEventType.THINK)
                        .sessionId(sessionId)
                        .modelId(modelId(session))
                        .content("恢复未完成工具调用")
                        .build());
            }
            finalAnswer = executePendingActing(session, toolSchemasJson, maxSteps, listener);
            if (finalAnswer != null && !finalAnswer.isBlank()) {
                if (shouldCompact(session)) {
                    compactService.compact(session);
                }
                sessionStore.save(session);
                return stripToolCallMarkers(finalAnswer);
            }
        }

        for (int step = 0; step < maxSteps; step++) {
            if (listener != null) {
                listener.onEvent(StreamingEvent.builder(StreamingEventType.THINK)
                        .sessionId(sessionId)
                        .modelId(modelId(session))
                        .content("第 " + (step + 1) + " 轮思考开始")
                        .build());
            }

            String assistantText = reasoning(session, toolSchemasJson, listener);
            List<ToolCall> toolCalls = toolCallParser.parse(assistantText, toolSchemasJson);

            // 将模型纯文本部分追加到历史，避免原始工具标记直接暴露
            String visibleText = stripToolCallMarkers(assistantText);
            if (!visibleText.isBlank()) {
                session.getHistory().add(new com.reasonix.agent.model.ChatMessage(com.reasonix.agent.model.ChatMessage.Role.ASSISTANT, visibleText));
            }

            if (toolCalls.isEmpty()) {
                finalAnswer = assistantText;
                break;
            }

            String actingResult = acting(session, toolCalls, toolSchemasJson, listener);
            if (actingResult != null && !actingResult.isBlank()) {
                finalAnswer = actingResult;
                break;
            }

            if (step == maxSteps - 1) {
                finalAnswer = summary(session, toolSchemasJson, listener);
            }
        }

        if (shouldCompact(session)) {
            compactService.compact(session);
        }

        sessionStore.save(session);

        if (finalAnswer == null || finalAnswer.isBlank()) {
            return "抱歉，我已耗尽最大执行步数，未能完成完整回答。";
        }
        return stripToolCallMarkers(finalAnswer);
    }

    /**
     * 显式 reasoning 阶段：仅发起模型调用并返回原始内容，不在此处追加 assistant 历史。
     */
    private String reasoning(Session session, String toolSchemasJson, StreamingEventListener listener) {
        List<com.reasonix.provider.ChatMessage> prompt = buildPrompt(session, toolSchemasJson);
        ChatRequest request = new ChatRequest(
                modelId(session),
                prompt,
                safeTemperature(),
                1024,
                false
        );

        ChatResponse response = chatModel.chat(request);
        String content = response.getContent() != null ? response.getContent() : "";

        if (listener != null) {
            listener.onEvent(StreamingEvent.builder(StreamingEventType.CHUNK)
                    .sessionId(session.getSessionId())
                    .modelId(modelId(session))
                    .content(content)
                    .build());
        }

        return content;
    }

    /**
     * 显式 acting 阶段：执行工具调用并返回 summary 文本；若执行失败，可由模型生成修正调用。
     */
    private String acting(Session session,
                          List<ToolCall> toolCalls,
                          String toolSchemasJson,
                          StreamingEventListener listener) {
        Map<String, String> toolResultMap = executeToolCallsWithRetry(session, toolCalls, toolSchemasJson, listener);

        List<String> toolResults = new ArrayList<>(toolResultMap.values());
        String toolResultText = String.join("\n", toolResults);
        session.getHistory().add(new com.reasonix.agent.model.ChatMessage(com.reasonix.agent.model.ChatMessage.Role.TOOL, toolResultText));

        boolean hasFailed = toolResults.stream().anyMatch(text ->
                text.contains("失败]") || text.contains("异常]") || text.contains("未知工具")
                        || text.contains("被权限策略拒绝") || text.contains("需要审批")
        );
        if (hasFailed) {
            return summary(session, toolSchemasJson, listener);
        }

        return null;
    }

    /**
     * 恢复上一轮遗留的工具调用执行。
     */
    private String executePendingActing(Session session,
                                       String toolSchemasJson,
                                       int maxSteps,
                                       StreamingEventListener listener) {
        List<ToolCall> pending = extractPendingToolCalls(session);
        if (pending.isEmpty()) {
            return null;
        }

        String actingResult = acting(session, pending, toolSchemasJson, listener);
        if (actingResult != null && !actingResult.isBlank()) {
            return actingResult;
        }

        for (int step = 1; step < maxSteps; step++) {
            String assistantText = reasoning(session, toolSchemasJson, listener);
            List<ToolCall> nextCalls = toolCallParser.parse(assistantText, toolSchemasJson);
            String visibleText = stripToolCallMarkers(assistantText);
            if (!visibleText.isBlank()) {
                session.getHistory().add(new com.reasonix.agent.model.ChatMessage(com.reasonix.agent.model.ChatMessage.Role.ASSISTANT, visibleText));
            }

            if (nextCalls.isEmpty()) {
                return assistantText;
            }

            String nextResult = acting(session, nextCalls, toolSchemasJson, listener);
            if (nextResult != null && !nextResult.isBlank()) {
                return nextResult;
            }
        }

        return summary(session, toolSchemasJson, listener);
    }

    /**
     * summary 兜底阶段：当工具执行失败或 maxSteps 耗尽时，让模型直接总结当前情况。
     */
    private String summary(Session session, String toolSchemasJson, StreamingEventListener listener) {
        List<com.reasonix.provider.ChatMessage> summaryPrompt = new ArrayList<>();
        summaryPrompt.add(new com.reasonix.provider.ChatMessage(com.reasonix.provider.ChatMessage.Role.SYSTEM,
                "You are Reasonix Java Agent. Now summarize the current situation and respond directly."));
        for (com.reasonix.agent.model.ChatMessage historyMessage : session.getHistory()) {
            summaryPrompt.add(new com.reasonix.provider.ChatMessage(
                    com.reasonix.provider.ChatMessage.Role.valueOf(historyMessage.getRole().name()),
                    historyMessage.getContent()
            ));
        }
        summaryPrompt.add(new com.reasonix.provider.ChatMessage(com.reasonix.provider.ChatMessage.Role.USER,
                "You have failed to generate response within the maximum iterations or tool execution failed."
                        + " Now respond directly by summarizing the current situation."));

        ChatRequest request = new ChatRequest(
                modelId(session),
                summaryPrompt,
                safeTemperature(),
                1024,
                false
        );
        ChatResponse response = chatModel.chat(request);
        String content = response.getContent() != null ? response.getContent() : "";

        if (listener != null) {
            listener.onEvent(StreamingEvent.builder(StreamingEventType.CHUNK)
                    .sessionId(session.getSessionId())
                    .modelId(modelId(session))
                    .content(content)
                    .build());
        }
        return content;
    }

    /**
     * 执行工具调用并支持失败后由模型纠错重试。
     */
    private Map<String, String> executeToolCallsWithRetry(Session session,
                                                          List<ToolCall> toolCalls,
                                                          String toolSchemasJson,
                                                          StreamingEventListener listener) {
        Map<String, String> toolResultMap = new LinkedHashMap<>();
        List<ToolCall> pendingCalls = new ArrayList<>(toolCalls);
        int retryAttempt = 0;
        final int maxToolRetries = 3;

        while (true) {
            boolean roundHasFailure = false;
            Map<String, String> roundResultMap = new LinkedHashMap<>();

            for (ToolCall toolCall : pendingCalls) {
                String toolName = toolCall.getToolName();

                if (listener != null) {
                    listener.onEvent(StreamingEvent.builder(StreamingEventType.TOOL_CALL)
                            .sessionId(session.getSessionId())
                            .modelId(modelId(session))
                            .toolName(toolName)
                            .arguments(toolCall.getArguments())
                            .content("调用工具：" + toolName)
                            .build());
                }

                Tool tool = toolRegistry.get(toolName);

                if (tool == null) {
                    roundResultMap.put(toolName, toolResultText(toolName, "未知工具 '" + toolName + "'"));
                    roundHasFailure = true;
                    continue;
                }

                PermissionGate.Result gateResult = permissionGate.check(
                        toolName,
                        toolCall.getArguments(),
                        session.getSessionId()
                );

                if (gateResult.decision() == PermissionGate.Decision.DENIED) {
                    roundResultMap.put(toolName, toolResultText(toolName, "工具 '" + toolName + "' 被权限策略拒绝。"));
                    roundHasFailure = true;
                    continue;
                }

                if (gateResult.decision() == PermissionGate.Decision.PENDING) {
                    String approvalId = gateResult.approval() != null ? gateResult.approval().getId() : "";
                    roundResultMap.put(toolName, toolResultText(toolName, "工具 '" + toolName + "' 需要审批，审批ID=" + approvalId + "。"));
                    roundHasFailure = true;
                    continue;
                }

                ToolContext ctx = ToolContext.of(session.getSessionId(), java.nio.file.Path.of("."));
                long start = System.currentTimeMillis();
                try {
                    ToolExecutionResult result = tool.execute(ctx, toolCall.getArguments());
                    long duration = System.currentTimeMillis() - start;
                    String resultText = result.isSuccess()
                            ? "[" + toolName + " 输出] " + result.getOutput()
                            : "[" + toolName + " 失败] " + result.getError();
                    roundResultMap.put(toolName, toolResultText(toolName, resultText));
                    toolResultMap.put(toolName, roundResultMap.get(toolName));

                    if (listener != null) {
                        listener.onEvent(StreamingEvent.builder(StreamingEventType.TOOL_RESULT)
                                .sessionId(session.getSessionId())
                                .modelId(modelId(session))
                                .toolName(toolName)
                                .toolResult(roundResultMap.get(toolName))
                                .content(roundResultMap.get(toolName))
                                .build());
                    }
                } catch (Exception e) {
                    String errorText = "[" + toolName + " 异常] " + e.getMessage();
                    roundResultMap.put(toolName, toolResultText(toolName, errorText));
                    roundHasFailure = true;

                    if (listener != null) {
                        listener.onEvent(StreamingEvent.builder(StreamingEventType.TOOL_RESULT)
                                .sessionId(session.getSessionId())
                                .modelId(modelId(session))
                                .toolName(toolName)
                                .toolResult(roundResultMap.get(toolName))
                                .content(roundResultMap.get(toolName))
                                .build());
                    }
                }
            }

            toolResultMap.putAll(roundResultMap);

            if (!roundHasFailure || retryAttempt >= maxToolRetries) {
                break;
            }

            retryAttempt++;
            if (listener != null) {
                listener.onEvent(StreamingEvent.builder(StreamingEventType.THINK)
                        .sessionId(session.getSessionId())
                        .modelId(modelId(session))
                        .content("工具调用执行失败，启动 LLM 纠错：第 " + retryAttempt + " 次尝试")
                        .build());
            }

            String correctionSummary = buildCorrectionSummary(roundResultMap);
            List<com.reasonix.provider.ChatMessage> correctionPrompt = buildPrompt(session, toolSchemasJson);
            correctionPrompt.add(new com.reasonix.provider.ChatMessage(com.reasonix.provider.ChatMessage.Role.USER, correctionSummary));
            ChatRequest correctionRequest = new ChatRequest(
                    modelId(session),
                    correctionPrompt,
                    safeTemperature(),
                    1024,
                    false
            );
            ChatResponse correctionResponse = chatModel.chat(correctionRequest);
            String correctionContent = correctionResponse.getContent() != null ? correctionResponse.getContent() : "";
            List<ToolCall> correctedCalls = toolCallParser.parse(correctionContent, toolSchemasJson);

            if (correctedCalls.isEmpty()) {
                break;
            }

            pendingCalls = correctedCalls;
        }

        return toolResultMap;
    }

    /**
     * 构建模型纠错 prompt：仅携带失败信息与可用工具 schema。
     */
    private String buildCorrectionSummary(Map<String, String> roundResultMap) {
        StringBuilder failedSummary = new StringBuilder("工具调用执行失败，请仅返回修正后的工具调用，不要返回任何解释文字：\n");
        for (Map.Entry<String, String> entry : roundResultMap.entrySet()) {
            if (isFailedResult(entry.getValue())) {
                failedSummary.append("- ").append(entry.getKey()).append("：").append(entry.getValue()).append("\n");
            }
        }
        return failedSummary.toString();
    }

    private boolean isFailedResult(String value) {
        return value != null && (
                value.contains("失败]")
                        || value.contains("异常]")
                        || value.contains("未知工具")
                        || value.contains("被权限策略拒绝")
                        || value.contains("需要审批")
        );
    }

    /**
     * 构造工具结果文本，便于后续写入会话历史。
     */
    private String toolResultText(String toolName, String message) {
        return "[" + toolName + "] " + message;
    }

    /**
     * 基于会话历史与工具 schema 构造 prompt；系统提示只在模型调用时拼接，不污染会话历史。
     */
    private List<com.reasonix.provider.ChatMessage> buildPrompt(Session session, String toolSchemasJson) {
        List<com.reasonix.provider.ChatMessage> prompt = new ArrayList<>();
        StringBuilder system = new StringBuilder();
        system.append("You are Reasonix Java Agent.");
        if (toolSchemasJson != null && !toolSchemasJson.isBlank() && !"[]".equals(toolSchemasJson)) {
            system.append("\n\nAvailable tools:\n").append(toolSchemasJson);
        }
        prompt.add(new com.reasonix.provider.ChatMessage(com.reasonix.provider.ChatMessage.Role.SYSTEM, system.toString()));
        for (com.reasonix.agent.model.ChatMessage historyMessage : session.getHistory()) {
            prompt.add(new com.reasonix.provider.ChatMessage(
                    com.reasonix.provider.ChatMessage.Role.valueOf(historyMessage.getRole().name()),
                    historyMessage.getContent()
            ));
        }
        return prompt;
    }

    private boolean hasPendingToolCalls(Session session) {
        List<com.reasonix.agent.model.ChatMessage> history = session.getHistory();
        if (history.isEmpty()) {
            return false;
        }
        com.reasonix.agent.model.ChatMessage last = history.get(history.size() - 1);
        if (last.getRole() != com.reasonix.agent.model.ChatMessage.Role.ASSISTANT) {
            return false;
        }
        List<ToolCall> toolCalls = toolCallParser.parse(last.getContent(), toolRegistry.getToolSchemasJson());
        if (toolCalls.isEmpty()) {
            return false;
        }
        // 仅当后续尚未出现足够 tool 结果时，视为 pending
        long toolResultCount = history.stream()
                .filter(m -> m.getRole() == com.reasonix.agent.model.ChatMessage.Role.TOOL)
                .count();
        return toolResultCount < toolCalls.size();
    }

    private List<ToolCall> extractPendingToolCalls(Session session) {
        List<com.reasonix.agent.model.ChatMessage> history = session.getHistory();
        if (history.isEmpty()) {
            return List.of();
        }
        com.reasonix.agent.model.ChatMessage last = history.get(history.size() - 1);
        if (last.getRole() != com.reasonix.agent.model.ChatMessage.Role.ASSISTANT) {
            return List.of();
        }
        List<ToolCall> toolCalls = toolCallParser.parse(last.getContent(), toolRegistry.getToolSchemasJson());
        if (toolCalls.isEmpty()) {
            return List.of();
        }

        long toolResultCount = history.stream()
                .filter(m -> m.getRole() == com.reasonix.agent.model.ChatMessage.Role.TOOL)
                .count();
        int pendingCount = toolCalls.size() - (int) toolResultCount;
        if (pendingCount <= 0) {
            return List.of();
        }
        return toolCalls.subList(0, pendingCount);
    }

    /**
     * 去除模型输出中明显的工具调用标记，避免将原始调用内容直接暴露给用户。
     *
     * <p>目前支持清洗：</p>
     * <ul>
     *   <li>自定义 XML 风格 tool_call 标签块</li>
     *   <li>[tool_use] ... [/tool_use] 兼容格式块</li>
     *   <li>function 风格标签块</li>
     * </ul>
     */
    private String stripToolCallMarkers(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        String cleaned = text;
        cleaned = cleaned.replaceAll("(?is)<tool_call[\\s\\S]*?>", "");
        cleaned = cleaned.replaceAll("(?is)\\[tool_use\\][\\s\\S]*?\\[/tool_use\\]", "");
        cleaned = cleaned.replaceAll("(?is)</?function[^>]*>", "");
        return cleaned.strip();
    }

    private String modelId(Session session) {
        return session.getModelId() != null ? session.getModelId() : reasonixConfig.getDefaultModel();
    }

    private int safeMaxSteps() {
        int maxSteps = reasonixConfig.getMaxSteps();
        return maxSteps > 0 ? maxSteps : 8;
    }

    private double safeTemperature() {
        double temperature = reasonixConfig.getTemperature();
        return temperature > 0 ? temperature : 0.7;
    }

    private boolean shouldCompact(Session session) {
        int maxHistorySize = 64;
        return compactService.shouldCompact(session, maxHistorySize);
    }
}
