package com.reasonix.subagent;

import com.reasonix.agent.loop.ReActLoop;
import com.reasonix.agent.model.Session;
import com.reasonix.agent.store.SessionStore;
import com.reasonix.config.ReasonixConfig;
import com.reasonix.provider.ChatModel;
import com.reasonix.tool.PermissionGate;
import com.reasonix.tool.ToolCallParser;
import com.reasonix.tool.ToolRegistry;
import org.springframework.stereotype.Component;

/**
 * 子 Agent 执行器。
 *
 * <p>在隔离子循环中执行 runAs=subagent 的 Skill，仅返回最终答案。
 */
@Component
public class SubagentExecutor {

    private final ChatModel chatModel;
    private final SessionStore sessionStore;
    private final SubagentRegistry subagentRegistry;
    private final ReasonixConfig reasonixConfig;
    private final ToolRegistry toolRegistry;
    private final ToolCallParser toolCallParser;
    private final PermissionGate permissionGate;

    public SubagentExecutor(ChatModel chatModel,
                            SessionStore sessionStore,
                            SubagentRegistry subagentRegistry,
                            ReasonixConfig reasonixConfig,
                            ToolRegistry toolRegistry,
                            ToolCallParser toolCallParser,
                            PermissionGate permissionGate) {
        this.chatModel = chatModel;
        this.sessionStore = sessionStore;
        this.subagentRegistry = subagentRegistry;
        this.reasonixConfig = reasonixConfig;
        this.toolRegistry = toolRegistry;
        this.toolCallParser = toolCallParser;
        this.permissionGate = permissionGate;
    }

    /**
     * 执行子 Agent。
     *
     * @param subagentName 子 Agent 名称
     * @param query 查询内容
     * @return 最终答案
     */
    public String execute(String subagentName, String query) {
        SubagentConfig config = subagentRegistry.get(subagentName)
                .orElseThrow(() -> new IllegalArgumentException("未找到子 Agent: " + subagentName));

        String sessionId = "subagent-" + subagentName + "-" + System.currentTimeMillis();
        Session session = sessionStore.create(sessionId);
        session.setModelId("default");

        ReActLoop loop = new ReActLoop(chatModel, sessionStore, reasonixConfig, toolRegistry, toolCallParser, permissionGate);
        return loop.execute(sessionId, query);
    }
}
