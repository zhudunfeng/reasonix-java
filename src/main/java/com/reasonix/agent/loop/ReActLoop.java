package com.reasonix.agent.loop;

import com.reasonix.agent.StreamingEvent;
import com.reasonix.agent.StreamingEventListener;
import com.reasonix.agent.StreamingEventType;
import com.reasonix.agent.model.Session;
import com.reasonix.agent.store.SessionStore;
import com.reasonix.config.ReasonixConfig;
import com.reasonix.provider.ChatModel;
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

/**
 * ReAct 核心执行循环。
 *
 * <p>执行流程：ComposePrompt → LLMCall → ParseToolCalls → ExecuteTools → PermissionGate → UpdateHistory → CheckDone → CompactIfNeeded。</p>
 *
 * <p>修复说明：当模型在回复中混入 {@code <tool_call>} 或 {@code [tool_use]} 工具调用标记时，
 * 当前循环会继续执行工具调用，但会把原始标记原文直接暴露给用户。
 * 此处补充清洗逻辑，确保最终返回的是纯文本答案。</p>
 */
@Component
public class ReActLoop {

    private final ChatModel chatModel;
    private final SessionStore sessionStore;
    private final ReasonixConfig reasonixConfig;
    private final ToolRegistry toolRegistry;
    private final ToolCallParser toolCallParser;
    private final PermissionGate permissionGate;

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

        // 最大步数限制
        int maxSteps = reasonixConfig.getMaxSteps() > 0 ? reasonixConfig.getMaxSteps() : 8;
        String toolSchemasJson = toolRegistry.getToolSchemasJson();

        // 最终答案
        String finalAnswer = null;

        // loop
        for (int step = 0; step < maxSteps; step++) {
            if (listener != null) {
                // 推送思考事件
                listener.onEvent(StreamingEvent.builder(StreamingEventType.THINK)
                        .sessionId(sessionId)
                        .modelId(session.getModelId() != null ? session.getModelId() : reasonixConfig.getDefaultModel())
                        .content("第 " + (step + 1) + " 轮思考开始")
                        .build());
            }

            // 构建提示: 系统提示与用户提示
            List<com.reasonix.provider.ChatMessage> prompt = buildPrompt(session, toolSchemasJson);
            ChatRequest request = new ChatRequest(
                    session.getModelId() != null ? session.getModelId() : reasonixConfig.getDefaultModel(),
                    prompt,
                    reasonixConfig.getTemperature(),
                    1024,
                    false
            );

            // 调用大模型
            ChatResponse response = chatModel.chat(request);
            String content = response.getContent() != null ? response.getContent() : "";

            if (listener != null) {
                listener.onEvent(StreamingEvent.builder(StreamingEventType.CHUNK)
                        .sessionId(sessionId)
                        .modelId(session.getModelId() != null ? session.getModelId() : reasonixConfig.getDefaultModel())
                        .content(content)
                        .build());
            }

            // 解析工具调用
            List<ToolCall> toolCalls = toolCallParser.parse(content, toolSchemasJson);
            if (toolCalls.isEmpty()) {
                // 无工具调用，直接返回文本结果
                finalAnswer = content;
                session.getHistory().add(new com.reasonix.agent.model.ChatMessage(com.reasonix.agent.model.ChatMessage.Role.ASSISTANT, content));
                break;
            }

            // 将模型原始回复（含工具调用）中的纯文本部分记录到历史，避免将工具标记原文暴露给用户
            String assistantText = stripToolCallMarkers(content);
            if (!assistantText.isBlank()) {
                session.getHistory().add(new com.reasonix.agent.model.ChatMessage(com.reasonix.agent.model.ChatMessage.Role.ASSISTANT, assistantText));
            }

            // 执行工具调用，支持失败后由大模型纠错并重新调用
            Map<String, String> toolResultMap = new LinkedHashMap<>();
            List<ToolCall> pendingCalls = new ArrayList<>(toolCalls);
            int retryAttempt = 0;
            final int maxToolRetries = 3;
            while (true) {
                boolean roundHasFailure = false;
                for (ToolCall toolCall : pendingCalls) {
                    String toolName = toolCall.getToolName();

                    if (listener != null) {
                        listener.onEvent(StreamingEvent.builder(StreamingEventType.TOOL_CALL)
                                .sessionId(sessionId)
                                .modelId(session.getModelId() != null ? session.getModelId() : reasonixConfig.getDefaultModel())
                                .toolName(toolName)
                                .arguments(toolCall.getArguments())
                                .content("调用工具：" + toolName)
                                .build());
                    }

                    Tool tool = toolRegistry.get(toolName);

                    if (tool == null) {
                        toolResultMap.put(toolName, "错误：未知工具 '" + toolName + "'");
                        roundHasFailure = true;
                        continue;
                    }

                    // 权限检查
                    PermissionGate.Result gateResult = permissionGate.check(
                            toolName,
                            toolCall.getArguments(),
                            session.getSessionId()
                    );

                    if (gateResult.decision() == PermissionGate.Decision.DENIED) {
                        toolResultMap.put(toolName, "错误：工具 '" + toolName + "' 被权限策略拒绝。");
                        roundHasFailure = true;
                        continue;
                    }

                    if (gateResult.decision() == PermissionGate.Decision.PENDING) {
                        String approvalId = gateResult.approval() != null ? gateResult.approval().getId() : "";
                        toolResultMap.put(toolName, "错误：工具 '" + toolName + "' 需要审批，审批ID=" + approvalId + "。");
                        roundHasFailure = true;
                        continue;
                    }

                    // 执行工具
                    ToolContext ctx = ToolContext.of(session.getSessionId(), java.nio.file.Path.of("."));
                    long start = System.currentTimeMillis();
                    try {
                        // 真正执行工具
                        ToolExecutionResult result = tool.execute(ctx, toolCall.getArguments());
                        long duration = System.currentTimeMillis() - start;
                        String resultText = result.isSuccess()
                                ? "[" + toolName + " 输出] " + result.getOutput()
                                : "[" + toolName + " 失败] " + result.getError();
                        toolResultMap.put(toolName, resultText);

                        if (listener != null) {
                            listener.onEvent(StreamingEvent.builder(StreamingEventType.TOOL_RESULT)
                                    .sessionId(sessionId)
                                    .modelId(session.getModelId() != null ? session.getModelId() : reasonixConfig.getDefaultModel())
                                    .toolName(toolName)
                                    .toolResult(resultText)
                                    .content(resultText)
                                    .build());
                        }
                    } catch (Exception e) {
                        String errorText = "[" + toolName + " 异常] " + e.getMessage();
                        toolResultMap.put(toolName, errorText);
                        roundHasFailure = true;

                        if (listener != null) {
                            listener.onEvent(StreamingEvent.builder(StreamingEventType.TOOL_RESULT)
                                    .sessionId(sessionId)
                                    .modelId(session.getModelId() != null ? session.getModelId() : reasonixConfig.getDefaultModel())
                                    .toolName(toolName)
                                    .toolResult(errorText)
                                    .content(errorText)
                                    .build());
                        }
                    }
                }

                // 如果没有失败或者超过最大工具调用重试，则跳出循环
                if (!roundHasFailure || retryAttempt >= maxToolRetries) {
                    break;
                }

                // 通过大模型纠错，生成修正后的工具调用
                retryAttempt++;
                if (listener != null) {
                    listener.onEvent(StreamingEvent.builder(StreamingEventType.THINK)
                            .sessionId(sessionId)
                            .modelId(session.getModelId() != null ? session.getModelId() : reasonixConfig.getDefaultModel())
                            .content("工具调用执行失败，启动 LLM 纠错：第 " + retryAttempt + " 次尝试")
                            .build());
                }

                StringBuilder failedSummary = new StringBuilder("工具调用执行失败，请仅返回修正后的工具调用，不要返回任何解释文字：\n");
                for (Map.Entry<String, String> entry : toolResultMap.entrySet()) {
                    if (entry.getValue().contains("失败]") || entry.getValue().contains("异常]")
                            || entry.getValue().contains("未知工具") || entry.getValue().contains("被权限策略拒绝")
                            || entry.getValue().contains("需要审批")) {
                        failedSummary.append("- ").append(entry.getKey()).append("：").append(entry.getValue()).append("\n");
                    }
                }

                List<com.reasonix.provider.ChatMessage> correctionPrompt = buildPrompt(session, toolSchemasJson);
                correctionPrompt.add(new com.reasonix.provider.ChatMessage(com.reasonix.provider.ChatMessage.Role.USER, failedSummary.toString()));
                ChatRequest correctionRequest = new ChatRequest(
                        session.getModelId() != null ? session.getModelId() : reasonixConfig.getDefaultModel(),
                        correctionPrompt,
                        reasonixConfig.getTemperature(),
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

            // 将工具结果追加到历史
            List<String> toolResults = new ArrayList<>(toolResultMap.values());
            String toolResultText = String.join("\n", toolResults);
            session.getHistory().add(new com.reasonix.agent.model.ChatMessage(com.reasonix.agent.model.ChatMessage.Role.TOOL, toolResultText));

            // 继续下一轮，让模型根据工具结果生成最终回复

            // 修复：如果这是最后一轮（maxSteps 已用完）且 toolCalls 不为空，
            // 将当前 LLM 的 content 作为 finalAnswer，避免 DONE 携带工具结果串。
            if (step == maxSteps - 1 && finalAnswer == null) {
                finalAnswer = content.trim();
            }
        }

        if (shouldCompact(session)) {
            new com.reasonix.agent.compact.CompactService().compact(session);
        }

        sessionStore.save(session);

        // 若因 maxSteps 用完而退出循环（finalAnswer 仍为 null），
        // 返回友好提示而非工具结果串
        if (finalAnswer == null || finalAnswer.isBlank()) {
            return "抱歉，我已耗尽最大执行步数，未能完成完整回答。";
        }

        return stripToolCallMarkers(finalAnswer);
    }

    /**
     * 去除模型输出中明显的工具调用标记，避免将原始调用内容直接暴露给用户。
     *
     * <p>目前支持清洗：</p>
     * <ul>
     *   <li>自定义 XML 风格 tool_call 标签块</li>
     *   <li>[tool_use] ... [/tool_use] 兼容格式块</li>
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

    private boolean shouldCompact(Session session) {
        int maxHistorySize = 64;
        return new com.reasonix.agent.compact.CompactService().shouldCompact(session, maxHistorySize);
    }
}
