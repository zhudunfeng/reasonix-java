package com.reasonix.provider;

import java.util.*;

/**
 * OpenAI 兼容适配器占位实现。
 *
 * <p>后续将替换为真实 HTTP 调用（WebClient / RestClient）。
 */
public class OpenAiCompatibleChatModel implements ChatModel {

    private final String modelId;

    public OpenAiCompatibleChatModel(String modelId) {
        this.modelId = modelId;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        // 占位：返回模拟响应，避免阻塞后续流程
        String content = "[占位响应] model=" + modelId + " messages=" + request.getMessages().size();
        return new ChatResponse(content, List.of(), "stop", new Usage(0, 0, 0));
    }

    @Override
    public boolean supportsStream() {
        return true;
    }
}
