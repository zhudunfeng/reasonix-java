package com.reasonix.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 工具调用解析器单元测试。
 */
class ToolCallParserTest {

    private final ToolCallParser parser = new ToolCallParser();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ========== tryParseJsonInner：纯 JSON 格式 ==========

    @Test
    void shouldParseToolCallsFromPlainJson() {
        String content = "{\"toolCalls\": [{\"tool\": \"web_fetch\", \"arguments\": {\"url\": \"https://example.com\"}}]}";

        List<ToolCall> calls = parser.parse(content, "[]");

        assertThat(calls).hasSize(1);
        assertThat(calls.get(0).getToolName()).isEqualTo("web_fetch");
        assertThat(calls.get(0).getArguments()).containsEntry("url", "https://example.com");
    }

    @Test
    void shouldParseSingleToolCallObject() {
        String content = "{\"tool\": \"read_file\", \"arguments\": {\"path\": \"/tmp/test.txt\"}}";

        List<ToolCall> calls = parser.parse(content, "[]");

        assertThat(calls).hasSize(1);
        assertThat(calls.get(0).getToolName()).isEqualTo("read_file");
    }

    @Test
    void shouldReturnEmptyWhenPlainJsonHasNoToolCalls() {
        String content = "{\"answer\": \"Hello, this is just a normal reply.\"}";

        List<ToolCall> calls = parser.parse(content, "[]");

        assertThat(calls).isEmpty();
    }

    // ========== tryParseJson：<think> 包裹格式 ==========

    @Test
    void shouldParseToolCallsFromThinkWrappedJson() {
        // 模拟 <think>\n思考过程\n</think>\n{"toolCalls":[{"tool":"web_fetch","arguments":{"url":"https://example.com"}}]}
        String thinkPart = "我来分析一下这个问题。\n";
        String jsonPart = "{\"toolCalls\": [{\"tool\": \"web_fetch\", \"arguments\": {\"url\": \"https://example.com\"}}]}";
        String content = "<think>\n" + thinkPart + "</think>\n" + jsonPart;

        List<ToolCall> calls = parser.parse(content, "[]");

        assertThat(calls).hasSize(1);
        assertThat(calls.get(0).getToolName()).isEqualTo("web_fetch");
        assertThat(calls.get(0).getArguments()).containsEntry("url", "https://example.com");
    }

    @Test
    void shouldParseToolCallsFromThinkWrappedJsonCompactForm() {
        // 模拟 <think>{内容}</think>\n{"toolCalls":[...]}
        String thinkPart = "需要查询天气信息。";
        String jsonPart = "{\"toolCalls\": [{\"tool\": \"web_fetch\", \"arguments\": {\"url\": \"https://wttr.in/Jinan\"}}]}";
        String content = "<think>" + thinkPart + "</think>\n" + jsonPart;

        List<ToolCall> calls = parser.parse(content, "[]");

        assertThat(calls).hasSize(1);
        assertThat(calls.get(0).getToolName()).isEqualTo("web_fetch");
        assertThat(calls.get(0).getArguments()).containsEntry("url", "https://wttr.in/Jinan");
    }

    // ========== 兜底行为：纯文本不解析出工具 ==========

    @Test
    void shouldReturnEmptyForPlainText() {
        String content = "这是一个普通回复，不包含任何工具调用。";

        List<ToolCall> calls = parser.parse(content, "[]");

        assertThat(calls).isEmpty();
    }

    @Test
    void shouldHandleNullContent() {
        List<ToolCall> calls = parser.parse(null, "[]");

        assertThat(calls).isEmpty();
    }

    @Test
    void shouldHandleEmptyContent() {
        List<ToolCall> calls = parser.parse("", "[]");

        assertThat(calls).isEmpty();
    }

    @Test
    void shouldParseToolCallsWithMultipleArguments() {
        String content = "{\"toolCalls\": [{\"tool\": \"shell\", \"arguments\": {\"command\": \"ls -la\", \"cwd\": \"/tmp\"}}]}";

        List<ToolCall> calls = parser.parse(content, "[]");

        assertThat(calls).hasSize(1);
        assertThat(calls.get(0).getToolName()).isEqualTo("shell");
        assertThat(calls.get(0).getArguments()).containsEntry("command", "ls -la");
        assertThat(calls.get(0).getArguments()).containsEntry("cwd", "/tmp");
    }

    @Test
    void shouldParseToolCallWithEmptyArguments() {
        String content = "{\"toolCalls\": [{\"tool\": \"list_dir\", \"arguments\": {}}]}";

        List<ToolCall> calls = parser.parse(content, "[]");

        assertThat(calls).hasSize(1);
        assertThat(calls.get(0).getToolName()).isEqualTo("list_dir");
        assertThat(calls.get(0).getArguments()).isEmpty();
    }
}
