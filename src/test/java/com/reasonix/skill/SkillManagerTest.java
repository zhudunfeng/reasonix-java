package com.reasonix.skill;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SkillManagerTest {

    @Test
    void shouldRegisterAndListSkill() {
        SkillManager manager = new SkillManager();
        Skill skill = new Skill("test", "desc", "body", SkillScope.PROJECT, SkillRunAs.INLINE, List.of(), null, 0);
        manager.register(skill);
        assertThat(manager.listAll()).hasSize(1);
        assertThat(manager.get("test")).isSameAs(skill);
    }
}
