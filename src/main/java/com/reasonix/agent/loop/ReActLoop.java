package com.reasonix.agent.loop;

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
 * <p>执行流程：ComposePrompt → LLMCall → ParseToolCalls → ExecuteTools → PermissionGate → UpdateHistory → CheckDone → CompactIfNeeded。
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
        Session session = sessionStore.get(sessionId).orElseGet(() -> sessionStore.create(sessionId));
        session.getHistory().add(new com.reasonix.agent.model.ChatMessage(com.reasonix.agent.model.ChatMessage.Role.USER, query));

        int maxSteps = reasonixConfig.getMaxSteps() > 0 ? reasonixConfig.getMaxSteps() : 8;
        String lastContent = query;
        String toolSchemasJson = toolRegistry.getToolSchemasJson();

        for (int step = 0; step < maxSteps; step++) {
            List<com.reasonix.provider.ChatMessage> prompt = buildPrompt(session, toolSchemasJson);
            ChatRequest request = new ChatRequest(
                    session.getModelId() != null ? session.getModelId() : reasonixConfig.getDefaultModel(),
                    prompt,
                    reasonixConfig.getTemperature(),
                    1024,
                    false
            );

            ChatResponse response = chatModel.chat(request);
            String content = response.getContent() != null ? response.getContent() : "";

            // 解析工具调用
            List<ToolCall> toolCalls = toolCallParser.parse(content, toolSchemasJson);
            if (toolCalls.isEmpty()) {
                // 无工具调用，直接返回文本结果
                lastContent = content;
                session.getHistory().add(new com.reasonix.agent.model.ChatMessage(com.reasonix.agent.model.ChatMessage.Role.ASSISTANT, content));
                break;
            }

            // 记录助手消息（包含工具调用）
            session.getHistory().add(new com.reasonix.agent.model.ChatMessage(com.reasonix.agent.model.ChatMessage.Role.ASSISTANT, content));

            // 执行工具调用
            List<String> toolResults = new ArrayList<>();
            for (ToolCall toolCall : toolCalls) {
                String toolName = toolCall.getToolName();
                Tool tool = toolRegistry.get(toolName);

                if (tool == null) {
                    toolResults.add("错误：未知工具 '" + toolName + "'");
                    continue;
                }

                // 权限检查
                PermissionGate.Result gateResult = permissionGate.check(
                        toolName,
                        toolCall.getArguments(),
                        session.getSessionId()
                );

                if (gateResult.decision() == PermissionGate.Decision.DENIED) {
                    toolResults.add("错误：工具 '" + toolName + "' 被权限策略拒绝。");
                    continue;
                }

                if (gateResult.decision() == PermissionGate.Decision.PENDING) {
                    String approvalId = gateResult.approval() != null ? gateResult.approval().getId() : "";
                    toolResults.add("错误：工具 '" + toolName + "' 需要审批，审批ID=" + approvalId + "。");
                    continue;
                }

                // 执行工具
                ToolContext ctx = ToolContext.of(session.getSessionId(), java.nio.file.Path.of("."));
                long start = System.currentTimeMillis();
                try {
                    ToolExecutionResult result = tool.execute(ctx, toolCall.getArguments());
                    long duration = System.currentTimeMillis() - start;
                    if (result.isSuccess()) {
                        toolResults.add("[" + toolName + " 输出] " + result.getOutput());
                    } else {
                        toolResults.add("[" + toolName + " 失败] " + result.getError());
                    }
                } catch (Exception e) {
                    toolResults.add("[" + toolName + " 异常] " + e.getMessage());
                }
            }

            // 将工具结果追加到历史
            String toolResultText = String.join("\n", toolResults);
            session.getHistory().add(new com.reasonix.agent.model.ChatMessage(com.reasonix.agent.model.ChatMessage.Role.TOOL, toolResultText));

            // 继续下一轮，让模型根据工具结果生成最终回复
            lastContent = toolResultText;
        }

        if (shouldCompact(session)) {
            new com.reasonix.agent.compact.CompactService().compact(session);
        }

        sessionStore.save(session);
        return lastContent;
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
