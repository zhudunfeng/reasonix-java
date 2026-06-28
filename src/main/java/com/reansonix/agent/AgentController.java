package com.reansonix.agent;

import com.reansonix.agent.loop.ReActLoop;
import com.reansonix.config.ReasonixConfig;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

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
     *
     * @param query 用户查询
     * @param sessionId 会话ID
     * @param modelId 模型ID（可选）
     * @return AgentResult 执行结果
     */
    public AgentResult execute(String query, String sessionId, String modelId) {
        if (query == null || query.isBlank()) {
            return AgentResult.builder().error("查询内容不能为空").build();
        }

        try {
            String result = reactLoop.execute(
                    sessionId != null ? sessionId : "default",
                    query
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
     *
     * <p>当前为最小可用实现：发送 START/DONE/ERROR 事件；后续可改为真流式 token 推送。
     */
    public void executeStream(String query,
                              String sessionId,
                              String modelId,
                              java.util.function.Consumer<StreamingEvent> onEvent) {
        if (onEvent == null) {
            return;
        }

        try {
            onEvent.accept(StreamingEvent.builder(StreamingEventType.START)
                    .sessionId(sessionId)
                    .modelId(modelId != null ? modelId : reasonixConfig.getDefaultModel())
                    .build());

            AgentResult result = execute(query, sessionId, modelId);

            if (result.getError() != null) {
                onEvent.accept(StreamingEvent.builder(StreamingEventType.ERROR)
                        .sessionId(sessionId)
                        .modelId(modelId)
                        .errorMessage(result.getError())
                        .build());
            } else {
                onEvent.accept(StreamingEvent.builder(StreamingEventType.DONE)
                        .sessionId(sessionId)
                        .modelId(modelId)
                        .content(result.getContent())
                        .compactTriggered(result.isCompactTriggered())
                        .build());
            }
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
