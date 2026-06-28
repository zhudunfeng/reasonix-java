package com.reansonix.skill;

import com.reansonix.skill.format.SkillFormat;
import com.reansonix.skill.format.SkillParseResult;
import com.reansonix.skill.format.SkillParser;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * OpenAI Agents / AGENTS.md 格式解析器
 * 文件名：AGENTS.md，内容即 body
 */
public class AgentsSkillParser implements SkillParser {
    @Override
    public SkillFormat supportedFormat() {
        return SkillFormat.AGENTS;
    }
    @Override
    public SkillParseResult parse(Path skillPath, Skill.Scope scope) {
        try {
            Path skillFile = Files.isDirectory(skillPath)
                    ? skillPath.resolve("AGENTS.md")
                    : skillPath;

            if (!Files.exists(skillFile)) {
                return SkillParseResult.error("AGENTS.md 不存在: " + skillPath);
            }

            String content = Files.readString(skillFile, StandardCharsets.UTF_8);
            String name = skillFile.getParent() != null
                    ? skillFile.getParent().getFileName().toString()
                    : "agents";

            Skill skill = new Skill(name, "OpenAI Agents 格式 Skill", content, scope,
                    Skill.RunAs.INLINE, List.of(), null, false, skillFile.toAbsolutePath().toString(), 0);

            return SkillParseResult.success(skill);
        } catch (Exception e) {
            return SkillParseResult.error("解析失败: " + e.getMessage());
        }
    }
}
