package com.reasonix.tool.builtin;

import com.reasonix.tool.Tool;
import com.reasonix.tool.ToolContext;
import com.reasonix.tool.ToolExecutionResult;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Component
public class EditFileTool implements Tool {
    @Override
    public String name() {
        return "edit_file";    }
    @Override
    public String description() {
        return "Replace a specific line in a text file after optional content verification.";    }
    @Override
    public Map<String, Object> schema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "path", Map.of("type", "string", "description", "File path to edit."),
                        "line", Map.of("type", "integer", "description", "1-based line number."),
                        "newContent", Map.of("type", "string", "description", "New line content."),
                        "oldContent", Map.of(
                                "type", "string",
                                "description", "Previous line content, used for validation."
                        )
                ),
                "required", List.of("path", "line", "newContent")
        );    }
    @Override
    public boolean readOnly() {
        return false;    }
    @Override
    public ToolExecutionResult execute(ToolContext ctx, Map<String, Object> arguments) {
        String path = (String) arguments.getOrDefault("path", "");
        Object lineObj = arguments.get("line");
        String newContent = (String) arguments.getOrDefault("newContent", "");
        String oldContent = (String) arguments.get("oldContent");

        if (path == null || path.isBlank()) {
            return ToolExecutionResult.error("Missing file path parameter (path)");        }
        if (lineObj == null) {
            return ToolExecutionResult.error("Missing line number parameter (line)");        }

        int line = (lineObj instanceof Number) ? ((Number) lineObj).intValue() : Integer.parseInt(lineObj.toString());
        if (line < 1) {
            return ToolExecutionResult.error("Line number must be >= 1");        }

        try {
            Path filePath = ctx.resolvePath(path);
            if (!Files.exists(filePath)) {
                return ToolExecutionResult.error("File not found: " + path);            }

            List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
            if (line > lines.size()) {
                return ToolExecutionResult.error("Line " + line + " exceeds file length (total " + lines.size() + " lines)");            }

            if (oldContent != null && !oldContent.equals(lines.get(line - 1))) {
                return ToolExecutionResult.error(
                        String.format("Line %d content does not match expectation.%nExpected: %s%nActual: %s",
                                line, oldContent, lines.get(line - 1)));            }

            lines.set(line - 1, newContent);
            Files.write(filePath, lines, StandardCharsets.UTF_8);

            return ToolExecutionResult.success(
                    String.format("Line %d has been updated:%n%s", line, line + " | " + newContent));
        } catch (Exception e) {
            return ToolExecutionResult.error("Failed to edit file: " + e.getMessage());        }    }
}