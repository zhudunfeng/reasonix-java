package com.reasonix.agent;

import com.reasonix.agent.loop.ReActLoop;
import com.reasonix.config.ReasonixConfig;
import org.springframework.stereotype.Component;

/**
 * Agent 统一控制器。
 *
 * <p>对外提供统一执行入口：execute / executeStream / compactSession。
 */
@Component
public class AgentController {

    private final ReActLoop reactLoop;
    private final ReasonixConfig reasonixConfig;

    public AgentController(ReActLoop reactLoop, ReasonixConfig reasonixConfig) {
        this.reactLoop = reactLoop;
        this.reasonixConfig = reasonixConfig;
    }

    /**
     * 同步执行 Agent 任务。
     */
    public AgentResult execute(String query, String sessionId, String modelId) {
        return execute(query, sessionId, modelId, null);
    }

    /**
     * 同步执行 Agent 任务，并可选推送中间事件。
     */
    public AgentResult execute(String query, String sessionId, String modelId, StreamingEventListener listener) {
        if (query == null || query.isBlank()) {
            return AgentResult.builder().error("查询内容不能为空").build();
        }

        try {
            String result = reactLoop.execute(
                    sessionId != null ? sessionId : "default",
                    query,
                    listener
            );

            return AgentResult.builder()
                    .content(result)
                    .sessionId(sessionId)
                    .modelId(modelId != null ? modelId : reasonixConfig.getDefaultModel())
                    .build();
        } catch (Exception e) {
            return AgentResult.builder()
                    .error("Agent 执行失败: " + e.getMessage())
                    .sessionId(sessionId)
                    .modelId(modelId)
                    .build();
        }
    }

    /**
     * 流式执行。
     */
    public void executeStream(String query,
                              String sessionId,
                              String modelId,
                              java.util.function.Consumer<StreamingEvent> onEvent) {
        executeStream(query, sessionId, modelId, onEvent, null);
    }

    /**
     * 流式执行（支持中间事件监听）。
     */
    public void executeStream(String query,
                              String sessionId,
                              String modelId,
                              java.util.function.Consumer<StreamingEvent> onEvent,
                              StreamingEventListener listener) {
        if (onEvent == null) {
            return;
        }

        try {
            onEvent.accept(StreamingEvent.builder(StreamingEventType.START)
                    .sessionId(sessionId)
                    .modelId(modelId != null ? modelId : reasonixConfig.getDefaultModel())
                    .build());

            AuditStreamingEventListener auditListener = new AuditStreamingEventListener();

            StreamingEventListener forwardingListener = event -> {
                auditListener.onEvent(event);
                if (listener != null) {
                    listener.onEvent(event);
                }
                onEvent.accept(event);
            };

            String result = reactLoop.execute(
                    sessionId != null ? sessionId : "default",
                    query != null ? query : "",
                    forwardingListener
            );

            onEvent.accept(StreamingEvent.builder(StreamingEventType.DONE)
                    .sessionId(sessionId)
                    .modelId(modelId)
                    .content(result)
                    .compactTriggered(false)
                    .build());
        } catch (Exception e) {
            onEvent.accept(StreamingEvent.builder(StreamingEventType.ERROR)
                    .sessionId(sessionId)
                    .modelId(modelId)
                    .errorMessage("流式执行失败: " + e.getMessage())
                    .build());
        }
    }

    /**
     * 压缩会话。
     */
    public String compactSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }

        // TODO: 接入 SessionStore + CompactService 的完整压缩逻辑
        return sessionId;
    }
}
