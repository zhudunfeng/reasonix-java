package com.reasonix.skill;

/**
 * Skill 解析结果 DTO。
 */
public class SkillPackage {

    private final String skillId;
    private final String format;
    private final String skillDir;
    private final String skillFile;
    private final String scripts;
    private final String metadata;

    public SkillPackage(String skillId, String format, String skillDir, String skillFile, String scripts, String metadata) {
        this.skillId = skillId;
        this.format = format;
        this.skillDir = skillDir;
        this.skillFile = skillFile;
        this.scripts = scripts;
        this.metadata = metadata;
    }

    public String getSkillId() {
        return skillId;
    }

    public String getFormat() {
        return format;
    }

    public String getSkillDir() {
        return skillDir;
    }

    public String getSkillFile() {
        return skillFile;
    }

    public String getScripts() {
        return scripts;
    }

    public String getMetadata() {
        return metadata;
    }
}
