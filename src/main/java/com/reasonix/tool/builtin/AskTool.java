package com.reasonix.tool.builtin;

import com.reasonix.tool.Tool;
import com.reasonix.tool.ToolContext;
import com.reasonix.tool.ToolExecutionResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 问答工具。
 *
 * <p>用于 Agent 向用户发起澄清问题，readOnly=true。
 */
@Component
public class AskTool implements Tool {

    @Override
    public String name() {
        return "ask";
    }

    @Override
    public String description() {
        return "向用户提问。用法: ask{\"question\":\"请确认要写入的文件路径\"}";
    }

    @Override
    public Map<String, Object> schema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "question", Map.of("type", "string", "description", "问题内容")
                ),
                "required", List.of("question")
        );
    }

    @Override
    public boolean readOnly() {
        return true;
    }

    @Override
    public ToolExecutionResult execute(ToolContext ctx, Map<String, Object> arguments) {
        String question = (String) arguments.getOrDefault("question", "");
        // 占位：真实场景需由前端/CLI 接收并回传答案
        return ToolExecutionResult.success("[待用户回答] " + question);
    }
}
