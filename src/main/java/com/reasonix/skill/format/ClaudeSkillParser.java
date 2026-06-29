package com.reasonix.skill;

import com.reasonix.skill.format.SkillFormat;
import com.reasonix.skill.format.SkillParseResult;
import com.reasonix.skill.format.SkillParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Claude Code / CLAUDE.md 格式解析器
 * 文件名：CLAUDE.md，内容即 body
 */
public class ClaudeSkillParser implements SkillParser {
    @Override
    public SkillFormat supportedFormat() {
        return SkillFormat.CLAUDE;
    }
    @Override
    public SkillParseResult parse(Path skillPath, Skill.Scope scope) {
        try {
            Path skillFile = Files.isDirectory(skillPath)
                    ? skillPath.resolve("CLAUDE.md")
                    : skillPath;

            if (!Files.exists(skillFile)) {
                return SkillParseResult.error("CLAUDE.md 不存在: " + skillPath);
            }

            String content = Files.readString(skillFile, StandardCharsets.UTF_8);
            String name = skillFile.getParent() != null
                    ? skillFile.getParent().getFileName().toString()
                    : "claude";

            Skill skill = new Skill(name, "Claude Code 格式 Skill", content, scope,
                    Skill.RunAs.INLINE, List.of(), null, false, skillFile.toAbsolutePath().toString(), 0);

            return SkillParseResult.success(skill);
        } catch (Exception e) {
            return SkillParseResult.error("解析失败: " + e.getMessage());
        }
    }
}
