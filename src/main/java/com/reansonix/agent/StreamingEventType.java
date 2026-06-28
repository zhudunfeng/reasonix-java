package com.reansonix.agent;

/**
 * 流式事件类型枚举
 */
public enum StreamingEventType {
    /**
     * 思考/计划片段
     */
    THINK,
    /**
     * 正在调用工具
     */
    TOOL_CALL,
    /**
     * 工具执行结果
     */
    TOOL_RESULT,
    /**
     * 文本增量输出
     */
    CHUNK,
    /**
     * 完成事件
     */
    DONE,
    /**
     * 开始事件
     */
    START,
    /**
     * 错误事件
     */
    ERROR
}

