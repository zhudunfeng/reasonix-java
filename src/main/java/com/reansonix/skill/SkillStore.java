package com.reansonix.skill;

import java.util.List;

/**
 * Skill 存储接口 - 管理 Skill 的加载、扫描和存储
 */
public interface SkillStore {
    /**
     * 加载指定目录下的所有 Skill
     */
    List<Skill> loadFromDirectory(java.nio.file.Path dir, Skill.Scope scope);
    /**
     * 加载单个 Skill 文件
     */
    Skill loadSkillFile(java.nio.file.Path file, Skill.Scope scope);
    /**
     * 获取所有已加载 Skill
     */
    List<Skill> listAll();
    /**
     * 清空所有 Skill
     */
    void clear();
}
