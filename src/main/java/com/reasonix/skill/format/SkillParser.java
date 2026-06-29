package com.reasonix.skill.format;

/**
 * Skill 解析器接口
 */
public interface SkillParser {
    /**
     * 支持的 Skill 格式
     */
    SkillFormat supportedFormat();

    /**
     * 解析 Skill 文件
     */
    SkillParseResult parse(java.nio.file.Path skillPath, com.reasonix.skill.Skill.Scope scope);
}
