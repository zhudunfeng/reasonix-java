package com.reansonix.controller;

import com.reansonix.subagent.SubagentConfig;
import com.reansonix.subagent.SubagentExecutor;
import com.reansonix.subagent.SubagentRegistry;
import com.reansonix.subagent.WorkspaceMode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Sub-Agent 控制器。
 */
@RestController
@RequestMapping("/api/subagents")
public class SubagentController {

    private final SubagentRegistry subagentRegistry;
    private final SubagentExecutor subagentExecutor;

    public SubagentController(SubagentRegistry subagentRegistry, SubagentExecutor subagentExecutor) {
        this.subagentRegistry = subagentRegistry;
        this.subagentExecutor = subagentExecutor;
    }

    @GetMapping
    public List<SubagentConfig> list() {
        return subagentRegistry.listAll();
    }

    @PostMapping("/execute")
    public Map<String, Object> execute(@RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        String query = (String) body.get("query");
        String result = subagentExecutor.execute(name, query);
        return Map.of("name", name, "result", result);
    }
}
