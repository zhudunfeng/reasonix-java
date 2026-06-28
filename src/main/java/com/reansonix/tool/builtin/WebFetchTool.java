package com.reansonix.tool.builtin;

import com.reansonix.tool.Tool;
import com.reansonix.tool.ToolContext;
import com.reansonix.tool.ToolExecutionResult;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class WebFetchTool implements Tool {
    @Override
    public String name() {
        return "web_fetch";
    }
    @Override
    public String description() {
        return "抓取网页内容（仅支持 http/https URL）。用法: web_fetch{\"url\": \"https://example.com\"}";
    }
    @Override
    public java.util.Map<String, Object> schema() {
        java.util.Map<String, Object> schema = new java.util.LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", java.util.Map.of(
                "url", java.util.Map.of("type", "string", "description", "要抓取的网页 URL（仅 http/https）")
        ));
        schema.put("required", java.util.List.of("url"));
        return schema;
    }
    @Override
    public boolean readOnly() {
        return true;
    }
    @Override
    public ToolExecutionResult execute(ToolContext ctx, java.util.Map<String, Object> arguments) {
        String url = (String) arguments.getOrDefault("url", "");
        if (url == null || url.isBlank()) {
            return ToolExecutionResult.error("缺少 URL 参数 (url)");
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return ToolExecutionResult.error("仅支持 http:// 或 https:// 开头 URL");
        }
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status >= 400) {
                return ToolExecutionResult.error("抓取失败，HTTP 状态码: " + status);
            }
            String body = response.body();
            return ToolExecutionResult.success(body);
        } catch (Exception e) {
            return ToolExecutionResult.error("抓取网页失败: " + e.getMessage());
        }
    }
}
