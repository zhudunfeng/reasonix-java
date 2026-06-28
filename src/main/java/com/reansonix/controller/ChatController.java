package com.reansonix.controller;

import com.reansonix.agent.AgentController;
import com.reansonix.agent.AgentResult;
import com.reansonix.agent.StreamingEvent;
import com.reansonix.agent.StreamingEventType;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 对话控制器 - 提供 Agent 对话接口与 SSE 流式输出。
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final AgentController agentController;

    public ChatController(AgentController agentController) {
        this.agentController = agentController;
    }

    /**
     * 同步对话接口。
     *
     * @param body 请求体，包含 query/sessionId/modelId
     * @return AgentResult
     */
    @PostMapping
    public AgentResult chat(@RequestBody Map<String, Object> body) {
        String query = (String) body.get("query");
        String sessionId = (String) body.getOrDefault("sessionId", "default");
        String modelId = (String) body.get("modelId");
        return agentController.execute(query, sessionId, modelId);
    }

    /**
     * Agent 请求接口。
     */
    @PostMapping("/agent/ask")
    public AgentResult agentAsk(@RequestBody Map<String, Object> body) {
        String question = (String) body.get("question");
        String sessionId = (String) body.getOrDefault("sessionId", "default");
        String modelId = (String) body.get("modelId");
        return agentController.execute(question != null ? question : "", sessionId, modelId);
    }

    /**
     * 强制启用 RAG。
     */
    @PostMapping("/rag/enabled")
    public Map<String, Object> ragEnabled(@RequestBody(required = false) Map<String, Object> body) {
        return Map.of("rag", true, "mode", "enabled");
    }

    /**
     * 禁用 RAG。
     */
    @PostMapping("/rag/disabled")
    public Map<String, Object> ragDisabled(@RequestBody(required = false) Map<String, Object> body) {
        return Map.of("rag", false, "mode", "disabled");
    }

    /**
     * 查询会话存在性。
     */
    @GetMapping("/session")
    public Map<String, Object> session(@RequestParam(defaultValue = "default") String conversationId) {
        // 占位：后续接入 SessionStore 查询
        return Map.of("conversationId", conversationId, "exists", false);
    }

    /**
     * SSE 流式对话接口。
     *
     * @param body 请求体
     * @return SseEmitter
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody Map<String, Object> body,
                                @RequestParam(defaultValue = "default") String sessionId,
                                @RequestParam(defaultValue = "default") String modelId) {
        String query = (String) body.get("query");

        SseEmitter emitter = new SseEmitter(300_000L);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                agentController.executeStream(query, sessionId, modelId, event -> {
                    try {
                        String payload = "data: " + toJson(event) + "\n\n";
                        emitter.send(payload);
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                });
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            } finally {
                executor.shutdown();
            }
        });
        return emitter;
    }

    /**
     * 获取会话历史（简化实现：当前返回空列表）。
     */
    @PostMapping("/history")
    public List<Object> history(@RequestBody Map<String, Object> body) {
        String sessionId = (String) body.get("sessionId");
        return List.of();
    }

    private String toJson(StreamingEvent event) {
        if (event == null) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"type\":\"").append(event.getType()).append("\",");
        sb.append("\"content\":\"").append(escape(event.getContent())).append("\",");
        sb.append("\"sessionId\":\"").append(escape(event.getSessionId())).append("\",");
        sb.append("\"modelId\":\"").append(escape(event.getModelId())).append("\"");
        sb.append("}");
        return sb.toString();
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
