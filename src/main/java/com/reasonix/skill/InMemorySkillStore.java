package com.reasonix.skill;

import com.reasonix.skill.format.SkillFormat;
import com.reasonix.skill.format.SkillFormatDetector;
import com.reasonix.skill.format.SkillParseResult;
import com.reasonix.skill.format.SkillParser;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * 内存 Skill 存储实现 - 扫描目录并解析 Skill
 */
@Component
public class InMemorySkillStore implements SkillStore {

    private final SkillFormatDetector formatDetector;
    private final AgentScopeSkillParser agentScopeParser = new AgentScopeSkillParser();
    private final AgentsSkillParser agentsParser = new AgentsSkillParser();
    private final ClaudeSkillParser claudeParser = new ClaudeSkillParser();

    private final Map<String, Skill> loadedSkills = new LinkedHashMap<>();
    private final List<String> loadedPaths = new ArrayList<>();

    public InMemorySkillStore() {
        this.formatDetector = new SkillFormatDetector();
    }

    @Override
    public List<Skill> loadFromDirectory(Path dir, Skill.Scope scope) {
        if (dir == null || !Files.isDirectory(dir)) {
            return List.of();
        }
        List<Skill> result = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(dir, 3)) {
            stream.filter(Files::exists)
                    .filter(p -> {
                        String name = p.getFileName() != null ? p.getFileName().toString() : "";
                        return "SKILL.md".equals(name)
                                || "AGENTS.md".equals(name)
                                || "CLAUDE.md".equals(name);
                    })
                    .forEach(p -> {
                        Skill skill = loadSkillFile(p, scope);
                        if (skill != null) {
                            result.add(skill);
                        }
                    });
        } catch (IOException e) {
            System.err.println("扫描 Skill 目录失败: " + dir + " - " + e.getMessage());
        }
        return result;
    }

    @Override
    public Skill loadSkillFile(Path file, Skill.Scope scope) {
        SkillFormat format = formatDetector.detectFromFile(file);
        SkillParseResult parseResult = switch (format) {
            case AGENTSCOPE -> agentScopeParser.parse(file, scope);
            case AGENTS -> agentsParser.parse(file, scope);
            case CLAUDE -> claudeParser.parse(file, scope);
            default -> SkillParseResult.error("不支持的 Skill 格式: " + format);
        };

        if (parseResult.isSuccess()) {
            loadedSkills.put(parseResult.getSkill().getName(), parseResult.getSkill());
            if (!loadedPaths.contains(file.toString())) {
                loadedPaths.add(file.toString());
            }
            return parseResult.getSkill();
        }
        return null;
    }

    @Override
    public List<Skill> listAll() {
        return new ArrayList<>(loadedSkills.values());
    }

    @Override
    public void clear() {
        loadedSkills.clear();
        loadedPaths.clear();
    }

    /**
     * 注册解析器（扩展用：支持注册外部格式解析器）
     */
    public void registerParser(SkillParser parser) {
    }
}
