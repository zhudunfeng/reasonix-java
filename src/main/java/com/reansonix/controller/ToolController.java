package com.reansonix.controller;

import com.reansonix.tool.ToolRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 工具控制器 - 提供工具查询与Schema接口。
 */
@RestController
@RequestMapping("/api/tools")
public class ToolController {

    private final ToolRegistry toolRegistry;

    public ToolController(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    /**
     * 获取所有工具名称。
     *
     * @return 工具名称列表
     */
    @GetMapping
    public List<String> list() {
        return toolRegistry.getNames();
    }

    /**
     * 根据名称获取工具Schema。
     *
     * @param name 工具名称
     * @return 工具Schema
     */
    @GetMapping("/{name}")
    public Map<String, Object> get(@PathVariable String name) {
        // 简化实现：返回占位Schema
        return Map.of(
                "name", name,
                "description", "工具描述占位",
                "schema", Map.of(
                        "type", "object",
                        "properties", Map.of()
                ),
                "readOnly", true
        );
    }

    /**
     * 获取所有工具Schema列表。
     *
     * @return Schema列表
     */
    @GetMapping("/schemas")
    public String schemas() {
        return toolRegistry.getToolSchemasJson();
    }
}
