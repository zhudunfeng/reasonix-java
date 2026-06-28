package com.reansonix.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * MCP 控制器 - 提供 MCP 工具调用接口。
 */
@RestController
@RequestMapping("/api/mcp")
public class McpController {

    /**
     * 列出可用 MCP 工具。
     */
    @PostMapping("/tools")
    public List<Map<String, Object>> tools() {
        com.reansonix.tool.ToolRegistry toolRegistry = null; // TODO: 注入
        // 占位实现
        return List.of();
    }

    /**
     * MCP 工具调用入口。
     *
     * @param body 请求体
     * @return 工具执行结果
     */
    @PostMapping("/call")
    public Map<String, Object> call(@RequestBody Map<String, Object> body) {
        String toolName = (String) body.get("tool");
        Map<String, Object> arguments = (Map<String, Object>) body.get("arguments");
        // TODO: 集成 ToolRegistry 执行真实工具
        return Map.of(
                "tool", toolName,
                "arguments", arguments,
                "result", "MCP 调用占位结果"
        );
    }
}
