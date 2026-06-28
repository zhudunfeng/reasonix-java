package com.reansonix.skill;

import com.reansonix.skill.format.SkillFormat;
import com.reansonix.skill.format.SkillFormatDetector;
import com.reansonix.skill.format.SkillParseResult;
import org.springframework.stereotype.Component;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * 动态 Skill 加载器。
 *
 * * <p>定时扫描目录，热更新 Skill Store。
 */
@Component
public class DynamicSkillLoader {

    private final SkillManager skillManager;
    private final SkillFormatDetector formatDetector;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public DynamicSkillLoader(SkillManager skillManager, SkillFormatDetector formatDetector) {
        this.skillManager = skillManager;
        this.formatDetector = formatDetector;
    }

    /**
     * 启动动态加载器。
     *
     * <p>当前版本为最小可用实现：不实际启动定时任务；
     * 后续将读取配置的 skill paths，定时扫描并热更新。
     */
    public void start() {
        // TODO: 读取配置路径并启动定时扫描
    }

    /**
     * 扫描目录并加载 Skill。
     */
    public void scanAndLoad(Path rootDir) {
        if (rootDir == null || !Files.isDirectory(rootDir)) {
            return;
        }
        try {
            Files.walk(rootDir).filter(path -> Files.isRegularFile(path) && path.toString().endsWith("SKILL.md")).forEach(path -> {
                try {
                    String content = Files.readString(path);
                    SkillFormat format = formatDetector.detect(path.getParent());
                    SkillParseResult result = switch (format) {
                        case AGENTSCOPE -> new com.reansonix.skill.AgentScopeSkillParser().parse(path.getParent(), com.reansonix.skill.Skill.Scope.PROJECT);
                        case AGENTS -> new com.reansonix.skill.AgentsSkillParser().parse(path.getParent(), com.reansonix.skill.Skill.Scope.PROJECT);
                        case CLAUDE -> new com.reansonix.skill.ClaudeSkillParser().parse(path.getParent(), com.reansonix.skill.Skill.Scope.PROJECT);
                        default -> com.reansonix.skill.format.SkillParseResult.error("不支持的 Skill 格式");
                    };
                    // 占位：转换为 Skill 并注册
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }
}
