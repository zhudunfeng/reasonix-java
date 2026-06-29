package com.reasonix.memory;

import com.reasonix.agent.model.ChatMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Prompt 组装器。
 *
 * * <p>按 PRD 设计拼接 Prompt 前缀与历史，保留可扩展入口。
 */
@Component
public class PromptComposer {

    private final MemorySet memorySet;
    private final VectorStore vectorStore;

    public PromptComposer(MemorySet memorySet, VectorStore vectorStore) {
        this.memorySet = memorySet;
        this.vectorStore = vectorStore;
    }

    /**
     * 组装完整 Prompt。
     *
     * @param query 用户查询
     * @param topK 记忆条数
     * @return Prompt 消息列表
     */
    public List<ChatMessage> compose(String query, int topK) {
        List<ChatMessage> prompt = new ArrayList<>();
        prompt.add(new ChatMessage(ChatMessage.Role.SYSTEM, "You are Reasonix Java Agent."));

        List<String> memories = memorySet.query(query, topK);
        if (!memories.isEmpty()) {
            StringBuilder memoryBlock = new StringBuilder("## Memory\n\n");
            for (String memory : memories) {
                memoryBlock.append("- ").append(memory).append("\n");
            }
            prompt.add(new ChatMessage(ChatMessage.Role.SYSTEM, memoryBlock.toString()));
        }

        List<String> rag = vectorStore.query(query, topK);
        if (!rag.isEmpty()) {
            StringBuilder ragBlock = new StringBuilder("## Retrieved Documents\n\n");
            for (String doc : rag) {
                ragBlock.append("- ").append(doc).append("\n");
            }
            prompt.add(new ChatMessage(ChatMessage.Role.SYSTEM, ragBlock.toString()));
        }

        prompt.add(new ChatMessage(ChatMessage.Role.USER, query));
        return prompt;
    }
}
