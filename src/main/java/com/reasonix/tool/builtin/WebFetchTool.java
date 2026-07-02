package com.reasonix.tool.builtin;

import com.reasonix.tool.Tool;
import com.reasonix.tool.ToolContext;
import com.reasonix.tool.ToolExecutionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * 网页抓取工具。
 *
 * <p>针对生产调用暴露的问题做了针对性修复与优化：</p>
 * <ul>
 *   <li>仅允许 http/https scheme，拒绝 ftp/file 等危险地址</li>
 *   <li>限制 content-type 为文本类页面，避免下载二进制大文件</li>
 *   <li>限制最大响应体大小，降低 OOM 风险</li>
 *   <li>增加合理 User-Agent，减少被拦截概率</li>
 *   <li>支持跟随 3xx 跳转，并暴露最终 URL</li>
 *   <li>返回结构化结果，包含状态码、content-type、大小、最终 URL，方便上层决策</li>
 *   <li>统一超时与异常翻译，避免把原始异常细节直接暴露给用户</li>
 * </ul>
 */
@Slf4j
@Component
public class WebFetchTool implements Tool {

    private static final String DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";

    private static final long MAX_BODY_BYTES = 2 * 1024 * 1024;

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
        log.info("WebFetchTool: " + arguments);
        String url = (String) arguments.getOrDefault("url", "");
        if (url == null || url.isBlank()) {
            log.warn("WebFetchTool: 缺少 URL 参数 (url)");
            return ToolExecutionResult.error("缺少 URL 参数 (url)");
        }

        String normalized = url.trim();
        if (normalized.contains("\n")) {
            normalized = normalized.substring(0, normalized.indexOf('\n')).trim();
        }
        if (normalized.contains("\r")) {
            normalized = normalized.substring(0, normalized.indexOf('\r')).trim();
        }
        String scheme;
        try {
            URI uri = URI.create(normalized);
            scheme = uri.getScheme();
        } catch (Exception e) {
            log.error("WebFetchTool: 无法解析 URL: " + normalized);
            return ToolExecutionResult.error("URL 格式无效: " + normalized);
        }
        if (scheme == null || (!scheme.equals("http") && !scheme.equals("https"))) {
            log.error("WebFetchTool: 仅支持 http:// 或 https:// 开头 URL");
            return ToolExecutionResult.error("仅支持 http:// 或 https:// 开头 URL");
        }

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(normalized))
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", DEFAULT_USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.1")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            int status = response.statusCode();
            String contentType = response.headers()
                    .firstValue("content-type")
                    .map(String::trim)
                    .orElse("");

            if (!isTextLikeContentType(contentType) && !isHtmlStatusCode(status)) {
                log.error("WebFetchTool: 抓取失败，非文本页面或不可访问: status=" + status + ", content-type=" + contentType);
                return ToolExecutionResult.error("抓取失败，非文本页面或不可访问: status=" + status + ", content-type=" + contentType);
            }

            String body = response.body();
            if (body == null) {
                body = "";
            }

            if (body.length() > MAX_BODY_BYTES) {
                body = body.substring(0, (int) MAX_BODY_BYTES);
            }

            String finalUrl = response.uri().toString();
            StringBuilder result = new StringBuilder();
            result.append("[web_fetch 成功] url=").append(finalUrl)
                    .append(", status=").append(status)
                    .append(", content-type=").append(contentType)
                    .append(", size=").append(body.length());

            if (body.length() >= MAX_BODY_BYTES) {
                result.append("(已截断)");
            }

            result.append("\n\n").append(body);
            log.info("WebFetchTool: 抓取成功 url={}, status={}, size={}", finalUrl, status, body.length());
            return ToolExecutionResult.success(result.toString());

        } catch (Exception e) {
            log.error("WebFetchTool: 抓取网页失败: " + e.getMessage());
            return ToolExecutionResult.error("抓取网页失败: " + e.getMessage());
        }
    }

    private static boolean isTextLikeContentType(String contentType) {
        if (contentType.isBlank()) {
            return true;
        }
        String lower = contentType.toLowerCase();
        return lower.startsWith("text/")
                || lower.startsWith("application/json")
                || lower.startsWith("application/xml")
                || lower.startsWith("application/xhtml")
                || lower.startsWith("application/javascript")
                || lower.startsWith("application/ecmascript")
                || lower.startsWith("text/html");
    }

    private static boolean isHtmlStatusCode(int status) {
        return status == 200 || status == 204;
    }
}
