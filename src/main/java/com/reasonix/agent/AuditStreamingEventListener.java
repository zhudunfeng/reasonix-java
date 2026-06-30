package com.reasonix.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 流式事件审计监听器：将中间事件输出到后端日志，便于排查执行过程。
 */
public class AuditStreamingEventListener implements StreamingEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditStreamingEventListener.class);

    @Override
    public void onEvent(StreamingEvent event) {
        if (event == null) {
            return;
        }

        String type = event.getType() != null ? event.getType().name() : "UNKNOWN";
        String content = event.getContent() != null ? event.getContent() : "";
        String toolName = event.getToolName() != null ? event.getToolName() : "";
        String toolResult = event.getToolResult() != null ? event.getToolResult() : "";

        StringBuilder sb = new StringBuilder();
        sb.append("[AgentStreamAudit] type=").append(type);
        if (event.getSessionId() != null) {
            sb.append(", sessionId=").append(event.getSessionId());
        }
        if (event.getModelId() != null) {
            sb.append(", modelId=").append(event.getModelId());
        }
        if (event.getArguments() != null && !event.getArguments().isEmpty()) {
            sb.append(", arguments=").append(event.getArguments());
        }
        if (event.getErrorMessage() != null) {
            sb.append(", errorMessage=").append(event.getErrorMessage());
        }
        if (event.getUsage() != null && !event.getUsage().isEmpty()) {
            sb.append(", usage=").append(event.getUsage());
        }
        sb.append(", compactTriggered=").append(event.isCompactTriggered());

        if (StreamingEventType.TOOL_CALL.equals(event.getType())) {
            sb.append(", toolName=").append(toolName);
            if (!content.isEmpty()) {
                sb.append(", content=").append(content);
            }
        } else if (StreamingEventType.TOOL_RESULT.equals(event.getType())) {
            sb.append(", toolName=").append(toolName);
            if (!toolResult.isEmpty()) {
                sb.append(", toolResult=").append(toolResult);
            }
        } else {
            if (!content.isEmpty()) {
                sb.append(", content=").append(content);
            }
        }

        LOGGER.info(sb.toString());
    }
}
