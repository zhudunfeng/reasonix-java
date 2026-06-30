package com.reasonix.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 工作区控制器 - 提供工作区与上下文查询接口。
 *
 * <p>当前为最小可用实现，返回模拟数据；后续接入真实 WorkspaceStore。
 */
@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

    /**
     * 列出工作区。
     *
     * @return 工作区列表
     */
    @GetMapping
    public List<Map<String, Object>> list() {
        return Arrays.asList(
                Map.of("path", "/workspaces/default", "name", "默认工作区", "active", true),
                Map.of("path", "/workspaces/project-a", "name", "项目 A", "active", false)
        );
    }

    /**
     * 选择工作区。
     *
     * @param body 请求体，可包含 path
     * @return 选择结果
     */
    @PostMapping("/pick")
    public Map<String, Object> pick(@RequestBody(required = false) Map<String, Object> body) {
        String path = body != null ? (String) body.get("path") : null;
        return Map.of("path", path == null ? "/workspaces/default" : path);
    }

    /**
     * 切换工作区。
     *
     * @param body 请求体，包含 path
     * @return 切换结果
     */
    @PostMapping("/switch")
    public Map<String, Object> switchWorkspace(@RequestBody Map<String, Object> body) {
        String path = (String) body.get("path");
        return Map.of("path", path);
    }

    /**
     * 查询上下文使用情况（简化实现）。
     *
     * @return 上下文信息
     */
    @GetMapping("/context")
    public Map<String, Object> context() {
        return Map.of(
                "usedTokens", 1234,
                "contextWindow", 8192,
                "remaining", 6958,
                "files", List.of(),
                "messages", List.of()
        );
    }

    /**
     * 列出工作区目录（简化实现）。
     *
     * @param path 目录路径
     * @return 目录条目
     */
    @GetMapping("/dir")
    public List<Map<String, Object>> dir(@RequestParam(defaultValue = "/") String path) {
        return Arrays.asList(
                Map.of("name", "src", "kind", "dir", "path", path + "/src"),
                Map.of("name", "README.md", "kind", "file", "path", path + "/README.md")
        );
    }
}
