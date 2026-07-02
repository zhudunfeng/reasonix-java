package com.reasonix.tool;

import com.reasonix.tool.builtin.BuiltinToolRegistrar;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 写操作预览实现。
 *
 * <p>基于工具名称和参数生成变更摘要，不执行实际写操作。
 */
@Component
public class DefaultPreviewer implements Previewer {

    private final ToolRegistry toolRegistry;

    public DefaultPreviewer(BuiltinToolRegistrar builtinToolRegistrar) {
        this.toolRegistry = builtinToolRegistrar.getRegistry();
    }

    @Override
    public Change preview(String toolName, Map<String, Object> arguments) {
        String summary = "预览工具: " + toolName;
        return new Change(summary, arguments);
    }

    @Override
    public boolean isReadOnly(String toolName, Map<String, Object> arguments) {
        if (toolName == null || toolName.isBlank()) {
            return false;
        }
        Tool tool = toolRegistry.get(toolName);
        if (tool == null) {
            return false;
        }
        return tool.readOnly();
    }
}
