package com.reasonix.tool;

import java.util.Map;

/**
 * 工具接口 - 所有内置/外部工具的统一抽象
 */
public interface Tool {
    /**
     * 工具名称（唯一标识）
     */
    String name();

    /**
     * 工具描述（用于 Prompt 工具列表和索引）
     */
    String description();

    /**
     * 工具参数的 JSON Schema 描述
     */
    Map<String, Object> schema();

    /**
     * 执行工具调用
     */
    ToolExecutionResult execute(ToolContext ctx, Map<String, Object> arguments);

    /**
     * 是否只读工具
     */
    boolean readOnly();

    /**
     * 获取工具分类
     */
    default String category() {
        return "general";
    }
}
