package com.reansonix.skill.format;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;


@Component
public class SkillFormatDetector {

    private static final Map<String, SkillFormat> CONVENTION_MAP = Map.of(
            ".reasonix", SkillFormat.AGENTSCOPE,
            ".agent", SkillFormat.AGENTSCOPE,
            ".agents", SkillFormat.AGENTS,
            ".claude", SkillFormat.CLAUDE
    );

    /**
     * 根据技能目录路径检测格式
     */
    public SkillFormat detect(Path skillDir) {
        if (skillDir == null || !Files.isDirectory(skillDir)) {
            return SkillFormat.UNKNOWN;
        }

        Path checkPath = skillDir;
        while (checkPath != null && !checkPath.equals(checkPath.getRoot())) {
            String dirName = checkPath.getFileName() != null ? checkPath.getFileName().toString() : "";
            if (CONVENTION_MAP.containsKey(dirName)) {
                return CONVENTION_MAP.get(dirName);
            }
            checkPath = checkPath.getParent();
        }

        Path skillMd = skillDir.resolve("SKILL.md");
        if (Files.exists(skillMd)) {
            return SkillFormat.AGENTSCOPE;
        }

        for (String fileName : List.of("AGENTS.md", "CLAUDE.md")) {
            Path file = skillDir.resolve(fileName);
            if (Files.exists(file)) {
                return fileName.equals("CLAUDE.md") ? SkillFormat.CLAUDE : SkillFormat.AGENTS;
            }
        }

        return SkillFormat.UNKNOWN;
    }

    /**
     * 从 skill.md 文件的 frontmatter 中检测格式
     */
    public SkillFormat detectFromFile(Path skillFile) {
        if (skillFile == null || !Files.exists(skillFile)) {
            return SkillFormat.UNKNOWN;
        }

        try {
            String content = Files.readString(skillFile, StandardCharsets.UTF_8);
            return detectFromContent(content);
        } catch (IOException e) {
            return SkillFormat.UNKNOWN;
        }
    }

    /**
     * 从文件内容中检测格式
     */
    public SkillFormat detectFromContent(String content) {
        if (content == null || content.isBlank()) {
            return SkillFormat.UNKNOWN;
        }

        if (content.contains("run_as:") || content.contains("run-as:")) {
            return SkillFormat.AGENTSCOPE;
        }
        if (content.contains("allowed-tools:")) {
            return SkillFormat.AGENTSCOPE;
        }

        return SkillFormat.UNKNOWN;
    }
}
