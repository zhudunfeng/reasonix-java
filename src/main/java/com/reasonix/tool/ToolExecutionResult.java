package com.reasonix.tool;

/**
 * 工具执行结果
 */
public class ToolExecutionResult {
    private final boolean success;
    private final String output;
    private final String error;
    private final long durationMs;

    private ToolExecutionResult(boolean success, String output, String error, long durationMs) {
        this.success = success;
        this.output = output;
        this.error = error;
        this.durationMs = durationMs;
    }

    public static ToolExecutionResult success(String output) {
        return new ToolExecutionResult(true, output, null, 0);
    }

    public static ToolExecutionResult success(String output, long durationMs) {
        return new ToolExecutionResult(true, output, null, durationMs);
    }

    public static ToolExecutionResult error(String error) {
        return new ToolExecutionResult(false, null, error, 0);
    }

    public static ToolExecutionResult error(String error, long durationMs) {
        return new ToolExecutionResult(false, null, error, durationMs);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getOutput() {
        return output;
    }

    public String getError() {
        return error;
    }

    public long getDurationMs() {
        return durationMs;
    }
}
