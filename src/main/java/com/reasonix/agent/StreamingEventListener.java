package com.reasonix.agent;

/**
 * Agent 流式事件监听器。
 */
@FunctionalInterface
public interface StreamingEventListener {

    /**
     * 处理 Agent 执行过程中的流式事件。
     *
     * @param event 流式事件
     */
    void onEvent(StreamingEvent event);
}
