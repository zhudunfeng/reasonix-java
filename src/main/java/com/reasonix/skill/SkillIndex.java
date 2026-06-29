package com.reasonix.skill;

import java.util.List;

/**
 * Skill 索引。
 *
 * * <p>仅包含 name + description，用于 Prompt 前缀；body 按需加载。
 */
public class SkillIndex {

    private final List<Skill> skills;

    public SkillIndex(List<Skill> skills) {
        this.skills = skills;
    }

    /**
     * 获取索引文本。
     */
    public String getIndexText() {
        StringBuilder sb = new StringBuilder("## Available Skills\n\n");
        for (Skill skill : skills) {
            sb.append("- **")
                    .append(skill.getName())
                    .append("**: ")
                    .append(skill.getDescription())
                    .append("\n");
        }
        return sb.toString();
    }
}
