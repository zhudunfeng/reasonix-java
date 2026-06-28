package com.reansonix.tool.builtin;

import com.reansonix.tool.Tool;
import com.reansonix.tool.ToolContext;
import com.reansonix.tool.ToolExecutionResult;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Bash 工具：执行 Shell 命令
 * readOnly=false，执行前需通过 Permission Gate 审批
 */
@Component
public class BashTool implements Tool {

    private final long timeoutSeconds;

    public BashTool() {
        this(60);
    }

    public BashTool(long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public String name() {
        return "bash";
    }

    @Override
    public String description() {
        return "执行 Shell 命令（支持超时、取消）。用法: bash{\"command\": \"ls -la\"}";
    }

    @Override
    public Map<String, Object> schema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "command", Map.of(
                        "type", "string",
                        "description", "要执行的 Shell 命令"
                ),
                "timeout", Map.of(
                        "type", "number",
                        "description", "超时秒数，默认60s"
                )
        ));
        schema.put("required", List.of("command"));
        return schema;
    }

    @Override
    public boolean readOnly() {
        return false;
    }

    @Override
    public ToolExecutionResult execute(ToolContext ctx, Map<String, Object> arguments) {
        String command = (String) arguments.getOrDefault("command", "");
        if (command == null || command.isBlank()) {
            return ToolExecutionResult.error("缺少命令参数 (command)");
        }

        long timeout = timeoutSeconds;
        if (arguments.containsKey("timeout")) {
            Object to = arguments.get("timeout");
            if (to instanceof Number) {
                timeout = ((Number) to).longValue();
            }
        }

        long startTime = System.currentTimeMillis();
        try {
            ProcessBuilder pb = new ProcessBuilder();
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                pb.command("cmd", "/c", command);
            } else {
                pb.command("sh", "-c", command);
            }
            pb.directory(ctx.getWorkingDirectory().toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            long durationMs = System.currentTimeMillis() - startTime;

            if (!finished) {
                process.destroyForcibly();
                return ToolExecutionResult.error(
                        "命令执行超时（" + timeout + "s）：" + command, durationMs);
            }

            int exitCode = finished ? process.exitValue() : -1;
            String result = output.toString();
            if (exitCode != 0) {
                return ToolExecutionResult.error(
                        "命令执行失败（exit code: " + exitCode + "）：\n" + result, durationMs);
            }
            return ToolExecutionResult.success(result.trim(), durationMs);
        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startTime;
            return ToolExecutionResult.error("执行异常: " + e.getMessage(), durationMs);
        }
    }
}
