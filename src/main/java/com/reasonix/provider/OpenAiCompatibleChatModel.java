package com.reasonix.provider;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容适配器实现。
 *
 * <p>根据配置的 baseUrl / apiKey / modelName 发起真实 HTTP 请求。</p>
 */
public class OpenAiCompatibleChatModel implements ChatModel {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private final String modelId;
    private final String modelName;
    private final String baseUrl;
    private final String apiKey;

    public OpenAiCompatibleChatModel(String modelId, String modelName, String baseUrl, String apiKey) {
        this.modelId = modelId;
        this.modelName = modelName;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        if (baseUrl == null || baseUrl.isBlank() || apiKey == null || apiKey.isBlank()) {
            List<String> missing = new ArrayList<>();
            if (baseUrl == null || baseUrl.isBlank()) {
                missing.add("baseUrl");
            }
            if (apiKey == null || apiKey.isBlank()) {
                missing.add("apiKey");
            }
            String message = String.format(
                    "[配置缺失] model=%s / modelName=%s 缺少 %s；请在 application.yml 中配置 supplier 的 baseUrl/apiKey，或设置对应环境变量后重启。",
                    modelId,
                    modelName != null && !modelName.isBlank() ? modelName : modelId,
                    String.join("、", missing)
            );
            return new ChatResponse(message, List.of(), "stop", Map.of(
                    "prompt_tokens", 0,
                    "completion_tokens", 0,
                    "total_tokens", 0
            ));
        }

        try {
            String endpoint = baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions";

            Map<String, Object> body = Map.of(
                    "model", modelName != null && !modelName.isBlank() ? modelName : modelId,
                    "messages", request.getMessages().stream()
                            .map(msg -> Map.of(
                                    "role", msg.getRole().name().toLowerCase(),
                                    "content", msg.getContent()
                            ))
                            .toList(),
                    "stream", false,
                    "temperature", request.getTemperature() != null ? request.getTemperature() : 0.7
            );

            String jsonBody = OBJECT_MAPPER.writeValueAsString(body);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofSeconds(60))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return new ChatResponse(
                        "[HTTP 错误] status=" + response.statusCode() + " body=" + response.body(),
                        List.of(),
                        "error",
                        Map.of(
                                "prompt_tokens", 0,
                                "completion_tokens", 0,
                                "total_tokens", 0
                        )
                );
            }

            Map<String, Object> responseBody = OBJECT_MAPPER.readValue(response.body(), Map.class);
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
            if (choices == null || choices.isEmpty()) {
                return new ChatResponse("[空响应] 模型未返回内容", List.of(), "stop", Map.of(
                        "prompt_tokens", 0,
                        "completion_tokens", 0,
                        "total_tokens", 0
                ));
            }

            Map<String, Object> firstChoice = choices.get(0);
            Map<String, Object> message = (Map<String, Object>) firstChoice.get("message");
            String content = (String) message.get("content");
            String finishReason = (String) firstChoice.get("finish_reason");

            Map<String, Object> usage = (Map<String, Object>) responseBody.get("usage");
            int promptTokens = toInt(usage.get("prompt_tokens"));
            int completionTokens = toInt(usage.get("completion_tokens"));
            int totalTokens = toInt(usage.get("total_tokens"));

            return new ChatResponse(
                    content != null ? content : "",
                    List.of(),
                    finishReason != null ? finishReason : "stop",
                    Map.of(
                            "prompt_tokens", promptTokens,
                            "completion_tokens", completionTokens,
                            "total_tokens", totalTokens
                    )
            );

        } catch (Exception e) {
            return new ChatResponse(
                    "[调用异常] " + e.getMessage(),
                    List.of(),
                    "error",
                    Map.of(
                            "prompt_tokens", 0,
                            "completion_tokens", 0,
                            "total_tokens", 0
                    )
            );
        }
    }

    private int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String str) {
            try {
                return Integer.parseInt(str);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    @Override
    public boolean supportsStream() {
        return true;
    }
}
