package com.reansonix.subagent;

import com.reansonix.config.ReasonixConfig;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 子 Agent 配置初始化器。
 *
 * * <p>应用启动时读取 YAML 配置并注册到 SubagentRegistry。
 */
@Component
public class SubagentConfigInitializer {

    private final SubagentRegistry subagentRegistry;

    public SubagentConfigInitializer(SubagentRegistry subagentRegistry) {
        this.subagentRegistry = subagentRegistry;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init(ReasonixConfig reasonixConfig) {
        if (reasonixConfig.getSubagent() == null
                || reasonixConfig.getSubagent().getAgents() == null) {
            return;
        }
        for (ReasonixConfig.Subagent.AgentDef agentDef : reasonixConfig.getSubagent().getAgents()) {
            WorkspaceMode mode = WorkspaceMode.SHARED;
            if (agentDef.getWorkspaceMode() != null) {
                mode = switch (agentDef.getWorkspaceMode().toLowerCase()) {
                    case "isolated" -> WorkspaceMode.ISOLATED;
                    case "read_only", "readonly" -> WorkspaceMode.READ_ONLY;
                    default -> WorkspaceMode.SHARED;
                };
            }
            SubagentConfig config = new SubagentConfig(
                    agentDef.getName(),
                    agentDef.getDescription(),
                    agentDef.getTools(),
                    mode,
                    reasonixConfig.getSubagent().getMaxParallel()
            );
            subagentRegistry.register(config);
        }
    }
}
