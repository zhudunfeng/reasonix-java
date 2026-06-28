package com.reansonix.agent.compact;

import com.reansonix.agent.model.Session;
import org.springframework.stereotype.Component;

/**
 * 会话压缩服务。
 *
 * <p>当会话历史超出 compactRatio 时，触发历史压缩，保留关键上下文。
 */
@Component
public class CompactService {

    private final double defaultCompactRatio = 0.8;

    public boolean shouldCompact(Session session, int maxHistorySize) {
        if (session == null || maxHistorySize <= 0) {
            return false;
        }
        int size = session.getHistory().size();
        return size > maxHistorySize && ((double) size / maxHistorySize) > defaultCompactRatio;
    }

    /**
     * 压缩会话历史。
     *
     * <p>当前版本为占位实现：仅保留最近一半消息；后续将接入摘要模型生成压缩上下文。
     */
    public void compact(Session session) {
        if (session == null) {
            return;
        }
        java.util.List<com.reansonix.agent.model.ChatMessage> history = session.getHistory();
        if (history.size() <= 2) {
            return;
        }
        int keep = Math.max(1, history.size() / 2);
        java.util.List<com.reansonix.agent.model.ChatMessage> compacted = new java.util.ArrayList<>(history.subList(history.size() - keep, history.size()));
        history.clear();
        history.addAll(compacted);
        session.setCompactTriggered(true);
    }
}
