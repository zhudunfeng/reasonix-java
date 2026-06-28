package com.reansonix.provider;

/**
 * 统一对话模型抽象接口。
 *
 * <p>所有供应商适配器均实现此接口，屏蔽不同 LLM 供应商的调用差异。
 */
public interface ChatModel {

    /**
     * 同步调用对话模型。
     *
     * @param request 对话请求
     * @return 对话响应
     */
    ChatResponse chat(ChatRequest request);

    /**
     * 判断当前模型是否支持流式输出。
     *
     * @return 是否支持流式
     */
    boolean supportsStream();
}
