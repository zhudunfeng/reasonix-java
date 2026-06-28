package com.reansonix.skill;

import com.reansonix.skill.format.SkillFormat;
import com.reansonix.skill.format.SkillFormatDetector;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Skill 注册中心 - 统一管理所有 Skill 的注册、查找和索引
 * 支持动态扫描和热加载
 */
@Component
public class SkillManager {

    private final Map<String, Skill> skills = new LinkedHashMap<>();
    private final Map<String, Skill> skillIndex = new LinkedHashMap<>();
    private final SkillFormatDetector formatDetector = new SkillFormatDetector();

    /**
     * 注册单个 Skill
     */
    public void register(Skill skill) {
        if (skill == null || skill.getName() == null) {
            return;
        }
        skills.put(skill.getName(), skill);
        skillIndex.merge(skill.getName(), skill, (existing, incoming) ->
                existing.getPriority() <= incoming.getPriority() ? existing : incoming);
    }

    /**
     * 批量注册 Skill
     */
    public void registerAll(List<Skill> skillList) {
        if (skillList == null) {
            return;
        }
        for (Skill skill : skillList) {
            register(skill);
        }
    }

    /**
     * 根据名称获取 Skill
     */
    public Skill get(String name) {
        return skills.get(name);
    }

    /**
     * 获取所有已注册 Skill
     */
    public List<Skill> listAll() {
        return new ArrayList<>(skills.values());
    }

    /**
     * 获取所有 subagent 类型的 Skill
     */
    public List<Skill> listSubagents() {
        return skills.values().stream()
                .filter(Skill::isSubagent)
                .toList();
    }

    /**
     * 获取 Skill 索引（name + description），用于 Prompt 前缀
     */
    public Map<String, String> getIndex() {
        Map<String, String> index = new LinkedHashMap<>();
        skills.values().stream()
                .sorted(Comparator.comparingInt(Skill::getPriority))
                .forEach(skill -> index.put(skill.getName(), skill.getDescription()));
        return index;
    }

    /**
     * 获取索引的文本表示（用于 Prompt）
     */
    public String getIndexText() {
        Map<String, String> index = getIndex();
        if (index.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("## Available Skills\n\n");
        for (Map.Entry<String, String> entry : index.entrySet()) {
            sb.append("- **").append(entry.getKey()).append("**: ").append(entry.getValue()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 获取格式检测器
     */
    public SkillFormatDetector getFormatDetector() {
        return formatDetector;
    }

    /**
     * 注销 Skill
     */
    public void unregister(String name) {
        skills.remove(name);
        skillIndex.remove(name);
    }

    /**
     * 禁用 Skill
     */
    public void disable(String name) {
        skillIndex.remove(name);
    }

    /**
     * 清空所有 Skill（热重载时使用）
     */
    public void clear() {
        skills.clear();
        skillIndex.clear();
    }
}
