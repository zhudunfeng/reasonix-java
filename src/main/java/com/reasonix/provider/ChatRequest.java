package com.reasonix.provider;

import java.util.List;

/**
 * 对话请求 DTO。
 *
 * <p>封装单轮对话所需的全部参数，屏蔽不同供应商的参数差异。
 */
public class ChatRequest {

    private final String modelId;
    private final List<ChatMessage> messages;
    private final Double temperature;
    private final Integer maxTokens;
    private final Boolean stream;

    public ChatRequest(String modelId, List<ChatMessage> messages, Double temperature, Integer maxTokens, Boolean stream) {
        this.modelId = modelId;
        this.messages = messages;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.stream = stream;
    }

    public String getModelId() {
        return modelId;
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public Double getTemperature() {
        return temperature;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public Boolean getStream() {
        return stream;
    }
}
