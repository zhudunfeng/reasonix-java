package com.reansonix.tool;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具审计中间件。
 *
 * <p>记录工具调用时间、参数、结果，用于调试和审计。
 */
@Component
public class ToolAuditMiddleware {

    private static final class AuditRecord {
        final String toolName;
        final Map<String, Object> arguments;
        final String result;
        final long startMs;
        final long durationMs;

        AuditRecord(String toolName, Map<String, Object> arguments, String result, long startMs, long durationMs) {
            this.toolName = toolName;
            this.arguments = arguments;
            this.result = result;
            this.startMs = startMs;
            this.durationMs = durationMs;
        }
    }

    private final List<AuditRecord> records = new ArrayList<>();
    private final Map<String, AuditRecord> latestByTool = new ConcurrentHashMap<>();

    public void record(String toolName, Map<String, Object> arguments, String result, long startMs, long durationMs) {
        AuditRecord record = new AuditRecord(toolName, arguments, result, startMs, durationMs);
        synchronized (records) {
            records.add(record);
        }
        latestByTool.put(toolName, record);
    }

    public List<AuditRecord> listAll() {
        synchronized (records) {
            return new ArrayList<>(records);
        }
    }

    public Optional<AuditRecord> latest(String toolName) {
        return Optional.ofNullable(latestByTool.get(toolName));
    }
}
