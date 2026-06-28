package com.reansonix.skill.format;

/**
 * Skill 解析结果
 */
public class SkillParseResult {
    private final boolean success;
    private final com.reansonix.skill.Skill skill;
    private final String error;

    private SkillParseResult(boolean success, com.reansonix.skill.Skill skill, String error) {
        this.success = success;
        this.skill = skill;
        this.error = error;
    }

    public static SkillParseResult success(com.reansonix.skill.Skill skill) {
        return new SkillParseResult(true, skill, null);
    }

    public static SkillParseResult error(String error) {
        return new SkillParseResult(false, null, error);
    }

    public boolean isSuccess() {
        return success;
    }

    public com.reansonix.skill.Skill getSkill() {
        return skill;
    }

    public String getError() {
        return error;
    }
}
