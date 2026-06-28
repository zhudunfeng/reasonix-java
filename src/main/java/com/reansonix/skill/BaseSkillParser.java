package com.reansonix.skill;

import com.reansonix.skill.format.SkillFormat;
import com.reansonix.skill.format.SkillParseResult;

import java.nio.file.Path;

/**
 * 基础 Skill 解析器。
 */
public interface BaseSkillParser {

    SkillParseResult parse(Path skillDir, String skillFile, String content);
}
