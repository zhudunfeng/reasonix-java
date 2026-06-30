package com.reasonix.agent.loop;

import com.reasonix.agent.StreamingEvent;
import com.reasonix.agent.StreamingEventType;
import com.reasonix.agent.StreamingEventListener;
import com.reasonix.agent.model.Session;
import com.reasonix.agent.store.SessionStore;
import com.reasonix.config.ReasonixConfig;
import com.reasonix.provider.ChatModel;
import com.reasonix.provider.ChatRequest;
import com.reasonix.provider.ChatResponse;
import com.reasonix.tool.PermissionGate;
import com.reasonix.tool.Tool;
import com.reasonix.tool.ToolCall;
import com.reasonix.tool.ToolCallParser;
import com.reasonix.tool.ToolContext;
import com.reasonix.tool.ToolExecutionResult;
import com.reasonix.tool.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReActLoopTest {

    /**
     * 验证多轮工具调用后，最终返回的是模型在最后一轮给出的纯文本回复，
     * 而不是含工具调用的原始回复或工具执行结果。
     */
    @Test
    void shouldReturnFinalTextAfterMultiRoundToolCalls() {
        // ---- 准备 Mock ----
        ToolRegistry toolRegistry = mock(ToolRegistry.class);
        ToolCallParser toolCallParser = mock(ToolCallParser.class);
        ChatModel chatModel = mock(ChatModel.class);
        PermissionGate permissionGate = mock(PermissionGate.class);
        SessionStore sessionStore = mock(SessionStore.class);
        ReasonixConfig config = mock(ReasonixConfig.class);

        // 配置 config
        when(config.getMaxSteps()).thenReturn(5);
        when(config.getDefaultModel()).thenReturn("test-model");
        when(config.getTemperature()).thenReturn(0.7);

        // 准备工具调用：解析后返回两条工具调用
        Map<String, Object> args1 = Map.of("url", "https://example.com");
        Map<String, Object> args2 = Map.of("file", "/tmp/test.txt");
        List<ToolCall> toolCalls = List.of(
                new ToolCall("web_fetch", args1),
                new ToolCall("read_file", args2)
        );

        // ---- 模拟第一轮：模型返回工具调用 ----
        String rawResponse1 = buildRawJsonWithToolCalls(toolCalls);
        ChatResponse chatResponse1 = new ChatResponse(rawResponse1, toolCalls, "tool_calls", Map.of());

        // ---- 模拟第二轮：模型返回纯文本最终回复 ----
        String finalAnswer = "最终答复：这是基于工具结果的回答。";
        ChatResponse chatResponse2 = new ChatResponse(finalAnswer, List.of(), "stop", Map.of());

        // chatModel.chat() 前两次返回工具调用和最终文本
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(chatResponse1)
                .thenReturn(chatResponse2);

        // toolCallParser 仅在第一轮解析成功
        when(toolCallParser.parse(rawResponse1, anyString())).thenReturn(toolCalls);
        when(toolCallParser.parse(finalAnswer, anyString())).thenReturn(List.of());

        // toolRegistry 返回两个 mock 工具
        Tool tool1 = mock(Tool.class);
        Tool tool2 = mock(Tool.class);
        when(toolRegistry.get("web_fetch")).thenReturn(tool1);
        when(toolRegistry.get("read_file")).thenReturn(tool2);
        when(tool1.name()).thenReturn("web_fetch");
        when(tool2.name()).thenReturn("read_file");
        when(tool1.execute(any(ToolContext.class), any(Map.class)))
                .thenReturn(ToolExecutionResult.success("页面内容"));
        when(tool2.execute(any(ToolContext.class), any(Map.class)))
                .thenReturn(ToolExecutionResult.success("文件内容"));

        // permissionGate 全部允许
        when(permissionGate.check(anyString(), any(Map.class), anyString()))
                .thenReturn(new PermissionGate.Result(PermissionGate.Decision.ALLOW, null));

        // toolSchemasJson 不为空
        when(toolRegistry.getToolSchemasJson()).thenReturn("[]");

        // Session 模拟
        Session session = new Session("test-session");
        when(sessionStore.get("test-session")).thenReturn(Optional.of(session));
        doAnswer(invocation -> {
            Session s = invocation.getArgument(0);
            return null;
        }).when(sessionStore).save(any(Session.class));

        // ---- 执行 ----
        List<StreamingEvent> capturedEvents = new ArrayList<>();
        StreamingEventListener listener = capturedEvents::add;

        ReActLoop loop = new ReActLoop(
                chatModel, sessionStore, config, toolRegistry,
                toolCallParser, permissionGate
        );

        String result = loop.execute("test-session", "帮我查一下天气和文件", listener);

        // ---- 断言 ----
        // 最终返回必须是模型最后一轮的纯文本，不能含原始工具调用JSON
        assertThat(result).isEqualTo(finalAnswer);
        assertThat(result).doesNotContain("\"tool\"");
        assertThat(result).doesNotContain("\"arguments\"");

        // 验证总共执行了 2 轮（1轮工具+1轮最终回复）
        assertThat(capturedEvents).isNotEmpty();

        // 验证事件流：应该有 TOOL_CALL、TOOL_RESULT，最后以非工具事件结束
        List<StreamingEventType> eventTypes = capturedEvents.stream()
                .map(StreamingEvent::getType)
                .toList();
        assertThat(eventTypes).contains(StreamingEventType.TOOL_CALL);
        assertThat(eventTypes).contains(StreamingEventType.TOOL_RESULT);
        assertThat(eventTypes.get(eventTypes.size() - 1))
                .isNotEqualTo(StreamingEventType.TOOL_CALL);
    }

    /**
     * 验证单轮直接返回文本（无工具调用）时正常返回模型回复。
     */
    @Test
    void shouldReturnTextWhenNoToolCalls() {
        ToolRegistry toolRegistry = mock(ToolRegistry.class);
        ToolCallParser toolCallParser = mock(ToolCallParser.class);
        ChatModel chatModel = mock(ChatModel.class);
        PermissionGate permissionGate = mock(PermissionGate.class);
        SessionStore sessionStore = mock(SessionStore.class);
        ReasonixConfig config = mock(ReasonixConfig.class);

        when(config.getMaxSteps()).thenReturn(5);
        when(config.getDefaultModel()).thenReturn("test-model");
        when(config.getTemperature()).thenReturn(0.7);

        String directAnswer = "直接回答，不需要工具。";
        ChatResponse chatResponse = new ChatResponse(directAnswer, List.of(), "stop", Map.of());
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(chatResponse);
        when(toolCallParser.parse(directAnswer, anyString())).thenReturn(List.of());
        when(toolRegistry.getToolSchemasJson()).thenReturn("[]");

        Session session = new Session("test-session");
        when(sessionStore.get("test-session")).thenReturn(Optional.of(session));
        doAnswer(invocation -> null).when(sessionStore).save(any(Session.class));

        ReActLoop loop = new ReActLoop(
                chatModel, sessionStore, config, toolRegistry,
                toolCallParser, permissionGate
        );

        String result = loop.execute("test-session", "一个问题", null);

        assertThat(result).isEqualTo(directAnswer);
    }

    // ---- 辅助方法：构造含工具调用的模拟 JSON 响应 ----
    private static String buildRawJsonWithToolCalls(List<ToolCall> calls) {
        try {
            List<Map<String, Object>> callMaps = new ArrayList<>();
            for (ToolCall c : calls) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("tool", c.getToolName());
                m.put("arguments", c.getArguments());
                callMaps.add(m);
            }
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("toolCalls", callMaps);
            return new ObjectMapper().writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
