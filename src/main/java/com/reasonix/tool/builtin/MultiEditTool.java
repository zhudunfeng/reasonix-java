package com.reasonix.tool.builtin;

import com.reasonix.tool.Tool;
import com.reasonix.tool.ToolContext;
import com.reasonix.tool.ToolExecutionResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 批量行级编辑工具。
 *
 * <p>readOnly=false，执行前需通过 Permission Gate 审批。
 */
@Component
public class MultiEditTool implements Tool {

    @Override
    public String name() {
        return "multi_edit";
    }

    @Override
    public String description() {
        return "批量行级文件编辑。用法: multi_edit{\"edits\": [{\"path\":\"a.txt\",\"old\":\"...\",\"new\":\"...\"}]}";
    }

    @Override
    public Map<String, Object> schema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "edits", Map.of(
                                "type", "array",
                                "description", "编辑列表",
                                "items", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "path", Map.of("type", "string", "description", "文件路径"),
                                                "old", Map.of("type", "string", "description", "旧文本"),
                                                "new", Map.of("type", "string", "description", "新文本")
                                        ),
                                        "required", List.of("path", "old", "new")
                                )
                        )
                ),
                "required", List.of("edits")
        );
    }

    @Override
    public boolean readOnly() {
        return false;
    }

    @Override
    public ToolExecutionResult execute(ToolContext ctx, Map<String, Object> arguments) {
        Object editsObj = arguments.get("edits");
        if (!(editsObj instanceof List)) {
            return ToolExecutionResult.error("缺少 edits 参数或格式不正确");
        }

        List<?> edits = (List<?>) editsObj;
        int success = 0;
        for (Object item : edits) {
            if (!(item instanceof Map)) {
                continue;
            }
            Map<?, ?> edit = (Map<?, ?>) item;
            String path = String.valueOf(edit.get("path"));
            String oldText = String.valueOf(edit.get("old"));
            String newText = String.valueOf(edit.get("new"));

            ToolExecutionResult result = new EditFileTool().execute(ctx, Map.of(
                    "path", path,
                    "old_string", oldText,
                    "new_string", newText
            ));
            if (result.isSuccess()) {
                success++;
            }
        }

        return ToolExecutionResult.success("批量编辑完成: " + success + "/" + edits.size());
    }
}
