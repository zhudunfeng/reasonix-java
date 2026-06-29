package com.reasonix.tool;

import com.reasonix.config.ReasonixConfig;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 写操作权限门。
 *
 * <p>当前为最小可用实现：
 * - 模式为 {@code allow} 时直接允许；
 * - 模式为 {@code deny} 时直接拒绝；
 * - 模式为 {@code none} 时直接允许；
 * - 模式为 {@code ask} 时创建待审批记录，由外部通过 {@link ToolApprovalController} 决策。
 */
@Component
public class PermissionGate {

    public enum Decision {
        ALLOWED, DENIED, PENDING
    }

    public record Result(Decision decision, ToolApproval approval) {
    }

    private final ReasonixConfig reasonixConfig;
    private final Previewer previewer;
    private final ToolApprovalStore toolApprovalStore;

    public PermissionGate(ReasonixConfig reasonixConfig,
                          Previewer previewer,
                          ToolApprovalStore toolApprovalStore) {
        this.reasonixConfig = reasonixConfig;
        this.previewer = previewer;
        this.toolApprovalStore = toolApprovalStore;
    }

    /**
     * 检查是否允许执行写操作。
     *
     * @param toolName 工具名称
     * @param arguments 参数
     * @param sessionId 会话ID
     * @return 决策结果
     */
    public Result check(String toolName, Map<String, Object> arguments, String sessionId) {
        String mode = "ask";
        if (reasonixConfig.getPermissions() != null && reasonixConfig.getPermissions().getMode() != null) {
            mode = reasonixConfig.getPermissions().getMode();
        }

        if ("allow".equalsIgnoreCase(mode) || "none".equalsIgnoreCase(mode)) {
            return new Result(Decision.ALLOWED, null);
        }

        if ("deny".equalsIgnoreCase(mode)) {
            return new Result(Decision.DENIED, null);
        }

        // 默认 ask 模式：创建审批记录
        Change change = previewer.preview(toolName, arguments);
        ToolApproval approval = toolApprovalStore.createPending(toolName, arguments != null ? arguments : Map.of(), sessionId);
        return new Result(Decision.PENDING, approval);
    }
}
