package com.reansonix.agent;

import com.reansonix.agent.compact.CompactService;
import com.reansonix.agent.model.Session;
import com.reansonix.agent.store.InMemorySessionStore;
import com.reansonix.agent.store.SessionStore;
import com.reansonix.config.ReasonixConfig;
import com.reansonix.provider.EmbeddingModel;
import com.reansonix.provider.ModelFactory;
import com.reansonix.provider.ModelRegistry;
import com.reansonix.skill.DynamicSkillLoader;
import com.reansonix.skill.SkillManager;
import com.reansonix.tool.ToolRegistry;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Agent 模块初始化配置。
 *
 * <p>负责启动时组装 SessionStore、SkillManager、ToolRegistry、ModelRegistry 等核心组件。
 */
@Component
public class HarnessAgentConfiguration implements CommandLineRunner {

    private final ModelRegistry modelRegistry;
    private final ModelFactory modelFactory;
    private final SessionStore sessionStore;
    private final SkillManager skillManager;
    private final DynamicSkillLoader dynamicSkillLoader;
    private final ToolRegistry toolRegistry;
    private final ReasonixConfig reasonixConfig;

    public HarnessAgentConfiguration(ModelRegistry modelRegistry,
                                     ModelFactory modelFactory,
                                     SessionStore sessionStore,
                                     SkillManager skillManager,
                                     DynamicSkillLoader dynamicSkillLoader,
                                     ToolRegistry toolRegistry,
                                     ReasonixConfig reasonixConfig) {
        this.modelRegistry = modelRegistry;
        this.modelFactory = modelFactory;
        this.sessionStore = sessionStore;
        this.skillManager = skillManager;
        this.dynamicSkillLoader = dynamicSkillLoader;
        this.toolRegistry = toolRegistry;
        this.reasonixConfig = reasonixConfig;
    }

    @Override
    public void run(String... args) {
        // 启动 Skill 动态加载器
        dynamicSkillLoader.start();

        // 打印启动摘要（中式注释）
        System.out.println("[Reasonix] Agent 模块初始化完成");
        System.out.println("[Reasonix] 可用模型数量: " + modelRegistry.getModelIds().size());
        System.out.println("[Reasonix] 已注册工具数量: " + toolRegistry.getNames().size());
        System.out.println("[Reasonix] 默认模型: " + reasonixConfig.getDefaultModel());
        System.out.println("[Reasonix] 最大执行轮数: " + reasonixConfig.getMaxSteps());
    }
}
