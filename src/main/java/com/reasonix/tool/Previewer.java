package com.reasonix.tool;

import java.util.Map;

/**
 * 写操作预览接口。
 *
 * <p>在不落盘的前提下生成变更摘要，供 Permission Gate 审批使用。
 */
public interface Previewer {

    /**
     * 预览变更。
     *
     * @param toolName 工具名称
     * @param arguments 参数
     * @return 变更摘要
     */
    Change preview(String toolName, Map<String, Object> arguments);

    /**
     * 判断是否为只读工具。
     *
     * <p>默认实现委托给 {@link ToolRegistry}，若工具不存在则返回 {@code false}。</p>
     */
    boolean isReadOnly(String toolName, Map<String, Object> arguments);
}
