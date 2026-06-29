package com.reasonix.tool;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具审批存储。
 *
 * <p>记录所有需要用户审批的写操作，支持按 ID、会话查询和决策。
 */
@Component
public class ToolApprovalStore {

    private final Map<String, ToolApproval> approvals = new ConcurrentHashMap<>();

    /**
     * 创建一条待审批记录。
     *
     * @param toolName 工具名
     * @param arguments 参数
     * @param sessionId 会话ID
     * @return 审批记录
     */
    public ToolApproval createPending(String toolName, Map<String, Object> arguments, String sessionId) {
        String id = UUID.randomUUID().toString();
        ToolApproval approval = new ToolApproval(id, toolName, arguments, sessionId);
        approvals.put(id, approval);
        return approval;
    }

    /**
     * 查询审批记录。
     *
     * @param id 审批ID
     * @return Optional 审批记录
     */
    public Optional<ToolApproval> findById(String id) {
        return Optional.ofNullable(approvals.get(id));
    }

    /**
     * 查询会话下的待审批记录。
     *
     * @param sessionId 会话ID
     * @return 待审批列表
     */
    public List<ToolApproval> findBySessionId(String sessionId) {
        return approvals.values().stream()
                .filter(a -> a.getSessionId().equals(sessionId) && !a.isApproved() && !a.isDenied())
                .toList();
    }

    /**
     * 审批通过。
     *
     * @param id 审批ID
     * @param reason 原因
     * @return 是否成功
     */
    public boolean approve(String id, String reason) {
        ToolApproval approval = approvals.get(id);
        if (approval == null || approval.isApproved() || approval.isDenied()) {
            return false;
        }
        approval.approve(reason);
        return true;
    }

    /**
     * 审批拒绝。
     *
     * @param id 审批ID
     * @param reason 原因
     * @return 是否成功
     */
    public boolean deny(String id, String reason) {
        ToolApproval approval = approvals.get(id);
        if (approval == null || approval.isApproved() || approval.isDenied()) {
            return false;
        }
        approval.deny(reason);
        return true;
    }
}
