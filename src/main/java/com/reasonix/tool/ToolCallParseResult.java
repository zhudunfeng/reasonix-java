package com.reasonix.tool;

import java.util.List;

/**
 * 工具调用解析结果，携带解析来源诊断信息。
 *
 * <p>通过 {@link ToolCallParser#parseWithDiagnostics(String, String)} 获取。
 * 调用方无需修改，仍可使用 {@code parse()} 方法获取纯工具调用列表。</p>
 *
 * @param toolCalls     解析出的工具调用列表；从未解析出则为空列表
 * @param parsedFromJson {@code true} = 从 JSON 格式解析成功；{@code false} = 从 XML fallback 解析或无结果
 */
public record ToolCallParseResult(
        List<ToolCall> toolCalls,
        boolean parsedFromJson
) {
    /**
     * 构造解析结果。
     *
     * @param toolCalls     工具调用列表（永不为 null，内部自动转为空列表）
     * @param parsedFromJson 是否从 JSON 格式解析成功
     */
    public ToolCallParseResult(List<ToolCall> toolCalls, boolean parsedFromJson) {
        this.toolCalls = toolCalls != null ? toolCalls : List.of();
        this.parsedFromJson = parsedFromJson;
    }

    /**
     * 便捷工厂方法：构造一个空结果（无工具调用）。
     *
     * @return 空的 ToolCallParseResult
     */
    public static ToolCallParseResult empty() {
        return new ToolCallParseResult(List.of(), false);
    }

    /**
     * 便捷工厂方法：构造一个 JSON 解析成功的结果。
     *
     * @param toolCalls 解析出的工具调用列表
     * @return 携带 JSON 标记的 ToolCallParseResult
     */
    public static ToolCallParseResult fromJson(List<ToolCall> toolCalls) {
        return new ToolCallParseResult(toolCalls, true);
    }

    /**
     * 便捷工厂方法：构造一个 XML fallback 解析成功的结果。
     *
     * @param toolCalls 解析出的工具调用列表
     * @return 携带 XML 标记的 ToolCallParseResult
     */
    public static ToolCallParseResult fromXml(List<ToolCall> toolCalls) {
        return new ToolCallParseResult(toolCalls, false);
    }
}
