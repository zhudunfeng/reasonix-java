package com.reasonix.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 会话控制器 - 提供会话管理接口。
 *
 * <p>当前为最小可用实现，返回模拟数据；后续接入真实 SessionStore。
 */
@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final AtomicLong idSeq = new AtomicLong(0);
    private final Map<Long, SessionMeta> sessions = new ConcurrentHashMap<>();

    public SessionController() {
        // 预置示例会话
        SessionMeta meta = new SessionMeta();
        meta.id = 1L;
        meta.title = "示例会话";
        meta.path = "/sessions/example.jsonl";
        meta.createdAt = Instant.now().minusSeconds(3600).toString();
        meta.updatedAt = Instant.now().toString();
        meta.turns = 2;
        meta.trashed = false;
        sessions.put(meta.id, meta);
    }

    /**
     * 列出可用会话。
     *
     * @param includeTrashed 是否包含已删除会话
     * @return 会话元信息列表
     */
    @GetMapping
    public List<SessionMeta> list(@RequestParam(defaultValue = "false") boolean includeTrashed) {
        return sessions.values().stream()
                .filter(it -> includeTrashed || !it.trashed)
                .toList();
    }

    /**
     * 列出已删除会话。
     *
     * @return 已删除会话列表
     */
    @GetMapping("/trashed")
    public List<SessionMeta> listTrashed() {
        return sessions.values().stream()
                .filter(SessionMeta::isTrashed)
                .toList();
    }

    /**
     * 恢复会话（从回收站）。
     *
     * @param path 会话路径
     * @return 恢复结果
     */
    @PostMapping("/restore")
    public Map<String, Object> restore(@RequestBody Map<String, Object> body) {
        String path = (String) body.get("path");
        sessions.values().stream()
                .filter(it -> path != null && path.equals(it.path))
                .findFirst()
                .ifPresent(it -> it.trashed = false);
        return Map.of("restored", path);
    }

    /**
     * 删除会话（软删除）。
     *
     * @param path 会话路径
     * @return 删除结果
     */
    @PostMapping("/delete")
    public Map<String, Object> delete(@RequestBody Map<String, Object> body) {
        String path = (String) body.get("path");
        sessions.values().stream()
                .filter(it -> path != null && path.equals(it.path))
                .findFirst()
                .ifPresent(it -> it.trashed = true);
        return Map.of("deleted", path);
    }

    /**
     * 永久删除会话。
     *
     * @param path 会话路径
     * @return 删除结果
     */
    @PostMapping("/purge")
    public Map<String, Object> purge(@RequestBody Map<String, Object> body) {
        String path = (String) body.get("path");
        List<Long> removed = new ArrayList<>();
        sessions.entrySet().removeIf(it -> {
            if (path != null && path.equals(it.getValue().path)) {
                removed.add(it.getKey());
                return true;
            }
            return false;
        });
        return Map.of("purged", removed.size(), "path", path);
    }

    /**
     * 重命名会话。
     *
     * @param path  会话路径
     * @param body  请求体，包含 title
     * @return 重命名结果
     */
    @PostMapping("/rename")
    public Map<String, Object> rename(@RequestBody Map<String, Object> body) {
        String path = (String) body.get("path");
        String title = (String) body.get("title");
        sessions.values().stream()
                .filter(it -> path != null && path.equals(it.path))
                .findFirst()
                .ifPresent(it -> it.title = title == null ? it.title : title);
        return Map.of("renamed", path, "title", title);
    }

    /**
     * 预览会话历史（简化实现）。
     *
     * @param path 会话路径
     * @return 历史消息列表
     */
    @GetMapping("/preview")
    public List<Map<String, Object>> preview(@RequestParam String path) {
        return Arrays.asList(
                Map.of("role", "user", "content", "你好"),
                Map.of("role", "assistant", "content", "你好，有什么可以帮你的？")
        );
    }

    /**
     * 会话元信息。
     */
    public static class SessionMeta {
        private long id;
        private String title;
        private String path;
        private String createdAt;
        private String updatedAt;
        private int turns;
        private boolean trashed;

        public long getId() { return id; }
        public String getTitle() { return title; }
        public String getPath() { return path; }
        public String getCreatedAt() { return createdAt; }
        public String getUpdatedAt() { return updatedAt; }
        public int getTurns() { return turns; }
        public boolean isTrashed() { return trashed; }
    }
}
