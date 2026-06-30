package com.reasonix.controller;

import com.reasonix.agent.AgentController;
import com.reasonix.agent.AgentResult;
import com.reasonix.agent.StreamingEvent;
import com.reasonix.config.ReasonixConfig;
import com.reasonix.provider.ModelRegistry;
import com.reasonix.provider.ModelResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AgentController agentController;
    private final ModelRegistry modelRegistry;
    private final ReasonixConfig reasonixConfig;

    public ChatController(AgentController agentController,
                          ModelRegistry modelRegistry,
                          ReasonixConfig reasonixConfig) {
        this.agentController = agentController;
        this.modelRegistry = modelRegistry;
        this.reasonixConfig = reasonixConfig;
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
        // 解析 modelId：若传入的是 "supplierId-modelName" 格式（如 stepfun-3.7-flash），
        // 自动映射为注册表中实际定义的 model id（如 step-3.7-flash）。
        modelId = ModelResolver.resolve(modelId, modelRegistry, reasonixConfig);
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
        // 解析 modelId：防止 "supplierId-modelName" 拼接格式导致配置缺失
        modelId = ModelResolver.resolve(modelId, modelRegistry, reasonixConfig);
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
        return Map.of("conversationId", conversationId, "exists", false);
    }

    /**
     * SSE 流式对话接口（POST 触发执行并返回事件流）。
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody Map<String, Object> body,
                                @RequestParam(defaultValue = "default") String sessionId,
                                @RequestParam(defaultValue = "default") String modelId) {
        String query = (String) body.get("query");
        // 解析 modelId：防止 URL 参数传入拼接格式（如 ?modelId=stepfun-3.7-flash）
        // 使用局部 final 变量，避免 lambda 捕获非 final 变量编译错误
        final String resolvedModelId = ModelResolver.resolve(modelId, modelRegistry, reasonixConfig);

        SseEmitter emitter = new SseEmitter(300_000L);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                agentController.executeStream(query, sessionId, resolvedModelId, event -> {
                    try {
                        String json = toJson(event);
                        emitter.send(SseEmitter.event()
                                .name("message")
                                .data(json));
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
     * SSE 流式对话接口（GET 触发执行并通过 SSE 推送中间事件）。
     *
     * <p>前端使用 EventSource 建立连接，服务端在连接建立后立即执行 Agent 并逐步推送
     * THINK / TOOL_CALL / TOOL_RESULT / CHUNK 事件，直至 DONE 或 ERROR。</p>
     *
     * @param question   用户问题（query 参数）
     * @param sessionId  会话ID
     * @param modelId    模型ID
     * @return SSE emitter
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStreamGet(@RequestParam(defaultValue = "default") String sessionId,
                                    @RequestParam(defaultValue = "") String question,
                                    @RequestParam(defaultValue = "") String modelId) {
        SseEmitter emitter = new SseEmitter(300_000L);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                agentController.executeStream(
                        question != null ? question : "",
                        sessionId,
                        modelId,
                        event -> {
                            try {
                                String json = toJson(event);
                                emitter.send(SseEmitter.event().name("message").data(json));
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        }
                );
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("message")
                            .data("{\"type\":\"error\",\"content\":\"" + e.getMessage() + "\"}"));
                } catch (IOException ex) {
                    emitter.completeWithError(ex);
                }
                emitter.complete();
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
        try {
            return OBJECT_MAPPER.writeValueAsString(event);
        } catch (Exception e) {
            return "{\"type\":\"error\",\"content\":\"序列化失败\"}";
        }
    }
}
