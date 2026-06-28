package com.reansonix.controller;

import com.reansonix.skill.Skill;
import com.reansonix.skill.SkillManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Skill 控制器 - 提供 Skill 的查询、注册与索引接口。
 */
@RestController
@RequestMapping("/api/skills")
public class SkillController {

    private final SkillManager skillManager;

    public SkillController(SkillManager skillManager) {
        this.skillManager = skillManager;
    }

    /**
     * 获取所有已注册 Skill。
     *
     * @return Skill 列表
     */
    @GetMapping
    public List<Skill> list() {
        return skillManager.listAll();
    }

    /**
     * 根据名称获取 Skill。
     *
     * @param name Skill 名称
     * @return Skill
     */
    @GetMapping("/{name}")
    public Skill get(@PathVariable String name) {
        return skillManager.get(name);
    }

    /**
     * 注册 Skill。
     *
     * @param body 请求体
     * @return 注册结果
     */
    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        String description = (String) body.get("description");
        String content = (String) body.get("content");
        // 简化实现：暂不解析复杂格式，仅做占位返回
        return Map.of(
                "name", name,
                "description", description,
                "registered", true
        );
    }

    /**
     * 获取 Skill 索引文本。
     *
     * @return 索引文本
     */
    @GetMapping("/index")
    public Map<String, String> index() {
        return skillManager.getIndex();
    }
}
