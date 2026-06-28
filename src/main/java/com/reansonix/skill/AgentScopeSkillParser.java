package com.reansonix.skill;

import com.reansonix.skill.format.SkillFormat;
import com.reansonix.skill.format.SkillParseResult;
import com.reansonix.skill.format.SkillParser;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * AgentScope 格式 SKILL.md 解析器
 */
@Component
public class AgentScopeSkillParser implements SkillParser {

    private static final Pattern FRONTMATTER_PATTERN = Pattern.compile("^---" + System.lineSeparator() + "(.*?)" + System.lineSeparator() + "---", Pattern.DOTALL);
    private static final Pattern KV_PATTERN = Pattern.compile("^([\\w\\-]+):\\s*(.*)$", Pattern.MULTILINE);
    private static final Pattern LIST_ITEM_PATTERN = Pattern.compile("^-\\s+(.*)$", Pattern.MULTILINE);

    @Override
    public SkillFormat supportedFormat() {
        return SkillFormat.AGENTSCOPE;
    }

    @Override
    public SkillParseResult parse(Path skillPath, Skill.Scope scope) {
        try {
            Path skillFile = Files.isDirectory(skillPath) ? skillPath.resolve("SKILL.md") : skillPath;

            if (!Files.exists(skillFile)) {
                return SkillParseResult.error("SKILL.md 不存在: " + skillPath);
            }

            String content = Files.readString(skillFile, StandardCharsets.UTF_8);
            Frontmatter fm = parseFrontmatter(content);

            String name = fm.get("name");
            if (name == null || name.isBlank()) {
                name = skillPath.getFileName() != null ? skillPath.getFileName().toString() : "unknown";
            }

            String body = extractBody(content);

            Skill.RunAs runAs = Skill.RunAs.INLINE;
            String runAsStr = fm.get("run_as");
            if ("subagent".equalsIgnoreCase(runAsStr) || "sub-agent".equalsIgnoreCase(runAsStr)) {
                runAs = Skill.RunAs.SUBAGENT;
            }

            List<String> allowedTools = parseList(fm.get("allowed_tools"));
            String model = fm.get("model");
            String effort = fm.get("effort");

            Skill skill = new Skill(name, fm.get("description"), body, scope, runAs,
                    allowedTools, model, false, skillFile.toAbsolutePath().toString(), 0);

            return SkillParseResult.success(skill);

        } catch (Exception e) {
            return SkillParseResult.error("解析失败: " + e.getMessage());
        }
    }

    private Frontmatter parseFrontmatter(String content) {
        Frontmatter fm = new Frontmatter();
        if (!content.startsWith("---")) return fm;

        int endIdx = content.indexOf("---", 3);
        if (endIdx < 0) return fm;

        String frontmatter = content.substring(3, endIdx).trim();
        parseFrontmatterLines(frontmatter, fm);
        return fm;
    }

    private void parseFrontmatterLines(String frontmatter, Frontmatter fm) {
        String[] lines = frontmatter.split(System.lineSeparator());
        String currentKey = null;
        StringBuilder currentValue = new StringBuilder();
        boolean inList = false;

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            if (line.startsWith("- ") && currentKey != null) {
                currentValue.append(line.substring(2)).append(System.lineSeparator());
                inList = true;
                continue;
            }

            Pattern kvPattern = Pattern.compile("^([\\w\\-]+):\\s*(.*)$");
            Matcher m = kvPattern.matcher(line);
            if (m.find()) {
                if (currentKey != null) {
                    fm.put(currentKey, currentValue.toString().trim());
                }
                currentKey = m.group(1).replace("-", "_");
                currentValue = new StringBuilder(m.group(2));
                inList = false;
            }
        }
        if (currentKey != null) {
            fm.put(currentKey, currentValue.toString().trim());
        }
    }

    private String extractBody(String content) {
        if (!content.startsWith("---")) return content;
        int endIdx = content.indexOf("---", 3);
        if (endIdx < 0) return content;
        return content.substring(endIdx + 3).trim();
    }

    private List<String> parseList(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(System.lineSeparator()))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    static class Frontmatter {
        private final Map<String, String> data = new LinkedHashMap<>();

        void put(String key, String value) {
            data.put(key, value);
        }

        String get(String key) {
            return data.get(key);
        }
    }
}
