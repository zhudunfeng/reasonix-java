package com.reasonix.skill;

import com.reasonix.skill.format.SkillParseResult;

import java.nio.file.Path;

/**
 * 基础 Skill 解析器。
 */
public interface BaseSkillParser {

    SkillParseResult parse(Path skillDir, String skillFile, String content);
}
