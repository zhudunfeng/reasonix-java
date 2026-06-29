package com.reasonix.tool;

import java.nio.file.Path;
import java.util.Map;

/**
 * 工具执行上下文 - 包含工作目录、会话ID等信息
 */
public class ToolContext {
    private final String sessionId;
    private final Path workingDirectory;
    private final Map<String, Object> metadata;

    private ToolContext(String sessionId, Path workingDirectory, Map<String, Object> metadata) {
        this.sessionId = sessionId;
        this.workingDirectory = workingDirectory;
        this.metadata = metadata;
    }

    public Path resolvePath(String path) {
        if (path == null || path.isBlank()) {
            return workingDirectory != null ? workingDirectory : Path.of(".");
        }
        Path p = Path.of(path);
        if (p.isAbsolute()) {
            return p.normalize();
        }
        return workingDirectory != null ? workingDirectory.resolve(p).normalize() : p.normalize();
    }

    public static ToolContext of(String sessionId, Path workingDirectory) {
        return new ToolContext(sessionId, workingDirectory, new java.util.HashMap<>());
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getSessionId() {
        return sessionId;
    }

    public Path getWorkingDirectory() {
        return workingDirectory;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public static class Builder {
        private String sessionId;
        private Path workingDirectory;
        private Map<String, Object> metadata = new java.util.HashMap<>();

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder workingDirectory(Path workingDirectory) {
            this.workingDirectory = workingDirectory;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public ToolContext build() {
            return new ToolContext(sessionId, workingDirectory, metadata);
        }
    }
}
