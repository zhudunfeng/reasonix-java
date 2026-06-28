package com.reansonix.controller;

import com.reansonix.tool.ToolApproval;
import com.reansonix.tool.ToolApprovalStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 工具审批控制器。
 */
@RestController
@RequestMapping("/api/tools/approvals")
public class ToolApprovalController {

    private final ToolApprovalStore toolApprovalStore;

    public ToolApprovalController(ToolApprovalStore toolApprovalStore) {
        this.toolApprovalStore = toolApprovalStore;
    }

    /**
     * 查询会话下的待审批列表。
     */
    @GetMapping
    public List<ToolApproval> list(@RequestParam(defaultValue = "default") String sessionId) {
        return toolApprovalStore.findBySessionId(sessionId);
    }

    /**
     * 查询单条审批记录。
     */
    @GetMapping("/{id}")
    public ResponseEntity<ToolApproval> get(@PathVariable String id) {
        return toolApprovalStore.findById(id)
                .<ResponseEntity<ToolApproval>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 审批通过。
     *
     * @param id 审批ID
     * @param body 请求体，可选 reason
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<Map<String, Object>> approve(@PathVariable String id,
                                                        @RequestBody(required = false) Map<String, Object> body) {
        String reason = body != null ? (String) body.getOrDefault("reason", "approved") : "approved";
        boolean ok = toolApprovalStore.approve(id, reason);
        if (!ok) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("success", false, "message", "审批记录不存在或已决策"));
        }
        return ResponseEntity.ok(Map.of("success", true, "id", id, "decision", "approved"));
    }

    /**
     * 审批拒绝。
     *
     * @param id 审批ID
     * @param body 请求体，可选 reason
     */
    @PostMapping("/{id}/deny")
    public ResponseEntity<Map<String, Object>> deny(@PathVariable String id,
                                                     @RequestBody(required = false) Map<String, Object> body) {
        String reason = body != null ? (String) body.getOrDefault("reason", "denied") : "denied";
        boolean ok = toolApprovalStore.deny(id, reason);
        if (!ok) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("success", false, "message", "审批记录不存在或已决策"));
        }
        return ResponseEntity.ok(Map.of("success", true, "id", id, "decision", "denied"));
    }
}
