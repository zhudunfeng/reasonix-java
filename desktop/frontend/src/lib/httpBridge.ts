/**
 * httpBridge 提供 Java 后端模式的 HTTP/SSE 桥接能力。
 *
 * <p>当 `VITE_BACKEND=java` 时，前端不再通过 Wails 绑定调用 Go 方法，而是通过
 * `HttpApp` 直接请求 Java Spring Boot 接口；事件流则通过 `SseBus` 从
 * `/api/chat/stream` 读取。这样 web/desktop 共用同一套后端契约。
 */

import { SseBus } from "./sseBus";

const JAVA_BASE = (import.meta.env.VITE_JAVA_BACKEND_URL ?? "").replace(/\/$/, "");

function javaUrl(path: string): string {
  if (!JAVA_BASE) return path;
  return `${JAVA_BASE}${path.startsWith("/") ? path : `/${path}`}`;
}

function ok(data: unknown): { ok: boolean; data: unknown } {
  return { ok: true, data };
}

function fail(err: unknown): { ok: boolean; error: string } {
  return { ok: false, error: err instanceof Error ? err.message : String(err) };
}

async function asJson(res: Response): Promise<unknown> {
  const text = await res.text();
  if (!text) return null;
  try {
    return JSON.parse(text) as unknown;
  } catch {
    return text;
  }
}

async function postJson(path: string, body: unknown): Promise<{ ok: boolean; data?: unknown; error?: string }> {
  try {
    const res = await fetch(javaUrl(path), {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body ?? {}),
    });
    if (!res.ok) {
      const data = await asJson(res);
      return fail({ status: res.status, data });
    }
    const data = await asJson(res);
    return ok(data);
  } catch (err) {
    return fail(err);
  }
}

async function getJson(path: string): Promise<{ ok: boolean; data?: unknown; error?: string }> {
  try {
    const res = await fetch(javaUrl(path), { method: "GET" });
    if (!res.ok) {
      const data = await asJson(res);
      return fail({ status: res.status, data });
    }
    const data = await asJson(res);
    return ok(data);
  } catch (err) {
    return fail(err);
  }
}

function asString(value: unknown, fallback = ""): string {
  if (value == null) return fallback;
  return String(value);
}

function asHistoryMessages(value: unknown): { role: string; content: string; toolName?: string; toolResult?: string; sessionId?: string; modelId?: string; turn?: number }[] {
  if (!Array.isArray(value)) return [];
  return value.map((it) => {
    const record = (it ?? {}) as Record<string, unknown>;
    return {
      role: asString(record.role, "assistant"),
      content: asString(record.content, ""),
      toolName: asString(record.toolName),
      toolResult: asString(record.toolResult),
      sessionId: asString(record.sessionId),
      modelId: asString(record.modelId),
      turn: typeof record.turn === "number" ? record.turn : undefined,
    };
  });
}

function asHistoryPage(value: unknown): { messages: { role: string; content: string; toolName?: string; toolResult?: string; sessionId?: string; modelId?: string; turn?: number }[]; totalTurns: number; hasOlder: boolean; startTurn: number; endTurn: number } {
  const record = (value ?? {}) as Record<string, unknown>;
  const messages = asHistoryMessages(record.messages);
  return {
    messages,
    totalTurns: typeof record.totalTurns === "number" ? record.totalTurns : messages.length,
    hasOlder: Boolean(record.hasOlder),
    startTurn: typeof record.startTurn === "number" ? record.startTurn : 0,
    endTurn: typeof record.endTurn === "number" ? record.endTurn : messages.length,
  };
}

function asSessionMeta(value: unknown): { path: string; title: string; updatedAt: string }[] {
  if (!Array.isArray(value)) return [];
  return value.map((it) => {
    const record = (it ?? {}) as Record<string, unknown>;
    return {
      path: asString(record.path, "/sessions/unknown.jsonl"),
      title: asString(record.title, "未命名会话"),
      updatedAt: asString(record.updatedAt, new Date().toISOString()),
    };
  });
}

function asWorkspaceView(value: unknown): { path: string; name: string; active: boolean }[] {
  if (!Array.isArray(value)) return [];
  return value.map((it) => {
    const record = (it ?? {}) as Record<string, unknown>;
    return {
      path: asString(record.path, "/workspaces/default"),
      name: asString(record.name, "未命名工作区"),
      active: Boolean(record.active),
    };
  });
}

function asPromptHistoryResult(value: unknown): { entries: { text: string; turn: number; at?: number; sessionPath?: string }[]; nonce: string; olderCursor: string; hasOlder: boolean } {
  const record = (value ?? {}) as Record<string, unknown>;
  const entries = Array.isArray(record.entries)
    ? record.entries.map((entry) => {
        const row = (entry ?? {}) as Record<string, unknown>;
        return {
          text: asString(row.text),
          turn: typeof row.turn === "number" ? row.turn : 0,
          at: typeof row.at === "number" ? row.at : undefined,
          sessionPath: asString(row.sessionPath),
        };
      })
    : [];
  return {
    entries,
    nonce: asString(record.nonce),
    olderCursor: asString(record.olderCursor),
    hasOlder: Boolean(record.hasOlder),
  };
}

export class HttpApp {
  private readonly eventBus: SseBus;
  private sessionId = "default";
  private modelId: string | undefined;

  constructor() {
    this.eventBus = new SseBus(javaUrl("/api/chat/stream"));
  }

  // 平台信息占位
  async Platform(): Promise<string> {
    return "web-java";
  }

  // 心跳占位
  async HeartbeatListTasks(): Promise<unknown> {
    return [];
  }
  async HeartbeatReloadTasks(): Promise<unknown> {
    return null;
  }
  async HeartbeatSaveTasks(_tasks: unknown): Promise<void> {
    // no-op
  }
  async HeartbeatTriggerNow(_id: string): Promise<void> {
    // no-op
  }
  async HeartbeatGenerateID(): Promise<string> {
    return `ht-${Math.random().toString(36).slice(2, 10)}`;
  }

  // Agent 控制入口
  async Submit(input: string): Promise<void> {
    await this.submitCore("Submit", input, this.sessionId, this.modelId);
  }
  async SubmitToTab(_tabID: string, input: string): Promise<void> {
    await this.Submit(input);
  }
  async SubmitDisplay(_display: string, input: string): Promise<void> {
    await this.submitCore("SubmitDisplay", input, this.sessionId, this.modelId);
  }
  async SubmitDisplayToTab(_tabID: string, _display: string, input: string): Promise<void> {
    await this.SubmitDisplay(_display, input);
  }
  async RunShell(command: string): Promise<void> {
    await this.submitCore("RunShell", command, this.sessionId, this.modelId);
  }
  async RunShellForTab(_tabID: string, command: string): Promise<void> {
    await this.RunShell(command);
  }
  async Steer(text: string): Promise<void> {
    await this.submitCore("Steer", text, this.sessionId, this.modelId);
  }
  async SteerForTab(_tabID: string, text: string): Promise<void> {
    await this.Steer(text);
  }
  async Cancel(): Promise<void> {
    await postJson("/api/chat/cancel", { sessionId: this.sessionId });
  }
  async CancelTab(_tabID: string): Promise<void> {
    await this.Cancel();
  }
  async Approve(id: string, allow: boolean, _session: boolean, _persist: boolean): Promise<void> {
    const path = allow ? "/api/tools/approvals/{id}/approve" : "/api/tools/approvals/{id}/deny";
    const body = allow
      ? { reason: "approved" }
      : { reason: "denied" };
    await postJson(path.replace("{id}", id), body);
  }
  async ApproveTab(tabID: string, id: string, allow: boolean, session: boolean, persist: boolean): Promise<void> {
    await this.Approve(id, allow, session, persist);
  }
  async AnswerQuestion(id: string, _answers: unknown[]): Promise<void> {
    await postJson("/api/tools/approvals/{id}/answer".replace("{id}", id), {});
  }
  async AnswerQuestionForTab(_tabID: string, id: string, answers: unknown[]): Promise<void> {
    await this.AnswerQuestion(id, answers);
  }
  async ReplayPendingPrompts(): Promise<void> {
    await postJson("/api/chat/replay", { sessionId: this.sessionId });
  }
  async SetPlanMode(_on: boolean): Promise<void> {
    await postJson("/api/chat/plan-mode", { sessionId: this.sessionId, on: _on });
  }
  async SetMode(mode: string): Promise<void> {
    await postJson("/api/chat/mode", { sessionId: this.sessionId, mode });
  }
  async SetModeForTab(_tabID: string, mode: string): Promise<void> {
    await this.SetMode(mode);
  }
  async SetAutoApproveTools(_on: boolean): Promise<void> {
    await postJson("/api/tools/approvals/auto-approve", { sessionId: this.sessionId, on: _on });
  }
  async SetCollaborationMode(mode: string): Promise<void> {
    await postJson("/api/chat/collaboration", { sessionId: this.sessionId, mode });
  }
  async SetCollaborationModeForTab(_tabID: string, mode: string): Promise<void> {
    await this.SetCollaborationMode(mode);
  }
  async SetToolApprovalMode(mode: string): Promise<void> {
    await postJson("/api/tools/approvals/mode", { sessionId: this.sessionId, mode });
  }
  async SetToolApprovalModeForTab(_tabID: string, mode: string): Promise<void> {
    await this.SetToolApprovalMode(mode);
  }
  async SetGoal(goal: string): Promise<void> {
    await postJson("/api/chat/goal", { sessionId: this.sessionId, goal });
  }
  async SetGoalForTab(_tabID: string, goal: string): Promise<void> {
    await this.SetGoal(goal);
  }
  async ClearGoal(): Promise<void> {
    await postJson("/api/chat/goal", { sessionId: this.sessionId, goal: "" });
  }
  async ClearGoalForTab(_tabID: string): Promise<void> {
    await this.ClearGoal();
  }
  async Compact(): Promise<void> {
    await postJson("/api/chat/compact", { sessionId: this.sessionId });
  }
  async NewSession(): Promise<void> {
    const result = await postJson("/api/chat/session", {});
    if (result.ok && result.data && typeof result.data === "object" && "sessionId" in result.data) {
      this.sessionId = asString((result.data as Record<string, unknown>).sessionId, this.sessionId);
    }
  }
  async ClearSession(): Promise<void> {
    await postJson("/api/chat/session", { sessionId: this.sessionId, clear: true });
  }

  // 历史与会话查询
  async History(): Promise<{ role: string; content: string; toolName?: string; toolResult?: string; sessionId?: string; modelId?: string; turn?: number }[]> {
    const result = await getJson(`/api/chat/history?sessionId=${encodeURIComponent(this.sessionId)}`);
    return result.ok ? asHistoryMessages(result.data) : [];
  }
  async HistoryForTab(_tabID: string): Promise<{ role: string; content: string; toolName?: string; toolResult?: string; sessionId?: string; modelId?: string; turn?: number }[]> {
    return this.History();
  }
  async HistoryPage(_beforeTurn: number, _limit: number): Promise<{ messages: { role: string; content: string; toolName?: string; toolResult?: string; sessionId?: string; modelId?: string; turn?: number }[]; totalTurns: number; hasOlder: boolean; startTurn: number; endTurn: number }> {
    const result = await getJson(`/api/chat/history/page?sessionId=${encodeURIComponent(this.sessionId)}&before=${_beforeTurn}&limit=${_limit}`);
    return result.ok ? asHistoryPage(result.data) : { messages: [], totalTurns: 0, hasOlder: false, startTurn: 0, endTurn: 0 };
  }
  async HistoryPageForTab(_tabID: string, beforeTurn: number, limit: number): Promise<{ messages: { role: string; content: string; toolName?: string; toolResult?: string; sessionId?: string; modelId?: string; turn?: number }[]; totalTurns: number; hasOlder: boolean; startTurn: number; endTurn: number }> {
    return this.HistoryPage(beforeTurn, limit);
  }
  async HistoryCheckpointTurnsForTab(_tabID: string): Promise<number[]> {
    return [];
  }
  async Checkpoints(): Promise<unknown[]> {
    return [];
  }
  async CheckpointsForTab(_tabID: string): Promise<unknown[]> {
    return [];
  }
  async Rewind(_turn: number, _scope: string): Promise<void> {
    await postJson("/api/chat/history/rewind", { sessionId: this.sessionId, turn: _turn, scope: _scope });
  }
  async Fork(_turn: number): Promise<unknown> {
    const result = await postJson("/api/chat/history/fork", { sessionId: this.sessionId, turn: _turn });
    if (result.ok && result.data && typeof result.data === "object" && "id" in result.data) {
      const data = result.data as Record<string, unknown>;
      return { id: asString(data.id), topicId: asString(data.topicId), title: asString(data.title) } as unknown;
    }
    return { id: "fork-unknown", topicId: "", title: "" };
  }
  async SummarizeFrom(_turn: number): Promise<void> {
    await postJson("/api/chat/history/summarize", { sessionId: this.sessionId, from: _turn });
  }
  async SummarizeUpTo(_turn: number): Promise<void> {
    await postJson("/api/chat/history/summarize", { sessionId: this.sessionId, to: _turn });
  }
  async ListSessions(): Promise<{ path: string; title: string; updatedAt: string }[]> {
    const result = await getJson("/api/sessions");
    return result.ok ? asSessionMeta(result.data) : [];
  }
  async ListTrashedSessions(): Promise<{ path: string; title: string; updatedAt: string }[]> {
    const result = await getJson("/api/sessions/trashed");
    return result.ok ? asSessionMeta(result.data) : [];
  }
  async ResumeSession(path: string): Promise<{ role: string; content: string; toolName?: string; toolResult?: string; sessionId?: string; modelId?: string; turn?: number }[]> {
    const result = await postJson("/api/sessions/resume", { path });
    return result.ok ? asHistoryMessages(result.data) : [];
  }
  async ResumeSessionForTab(_tabID: string, path: string): Promise<{ role: string; content: string; toolName?: string; toolResult?: string; sessionId?: string; modelId?: string; turn?: number }[]> {
    return this.ResumeSession(path);
  }
  async ResumeSessionPage(path: string, limit: number): Promise<{ messages: { role: string; content: string; toolName?: string; toolResult?: string; sessionId?: string; modelId?: string; turn?: number }[]; totalTurns: number; hasOlder: boolean; startTurn: number; endTurn: number }> {
    const result = await postJson("/api/sessions/resume/page", { path, limit });
    return result.ok ? asHistoryPage(result.data) : { messages: [], totalTurns: 0, hasOlder: false, startTurn: 0, endTurn: 0 };
  }
  async ResumeSessionPageForTab(_tabID: string, path: string, limit: number): Promise<{ messages: { role: string; content: string; toolName?: string; toolResult?: string; sessionId?: string; modelId?: string; turn?: number }[]; totalTurns: number; hasOlder: boolean; startTurn: number; endTurn: number }> {
    return this.ResumeSessionPage(path, limit);
  }
  async OpenChannelSessionForTab(_tabID: string, path: string): Promise<{ role: string; content: string; toolName?: string; toolResult?: string; sessionId?: string; modelId?: string; turn?: number }[]> {
    return this.ResumeSession(path);
  }
  async OpenChannelSessionPageForTab(_tabID: string, path: string, limit: number): Promise<{ messages: { role: string; content: string; toolName?: string; toolResult?: string; sessionId?: string; modelId?: string; turn?: number }[]; totalTurns: number; hasOlder: boolean; startTurn: number; endTurn: number }> {
    return this.ResumeSessionPage(path, limit);
  }
  async PreviewSession(path: string): Promise<{ role: string; content: string; toolName?: string; toolResult?: string; sessionId?: string; modelId?: string; turn?: number }[]> {
    const result = await getJson(`/api/sessions/preview?path=${encodeURIComponent(path)}`);
    return result.ok ? asHistoryMessages(result.data) : [];
  }
  async DeleteSession(path: string): Promise<void> {
    await postJson("/api/sessions/delete", { path });
  }
  async RestoreSession(path: string): Promise<void> {
    await postJson("/api/sessions/restore", { path });
  }
  async PurgeTrashedSession(path: string): Promise<void> {
    await postJson("/api/sessions/purge", { path });
  }
  async RenameSession(path: string, title: string): Promise<void> {
    await postJson("/api/sessions/rename", { path, title });
  }
  async ScanPromptHistory(nonce: string): Promise<{ entries: { text: string; turn: number; at?: number; sessionPath?: string }[]; nonce: string; olderCursor: string; hasOlder: boolean }> {
    const result = await postJson("/api/chat/prompt-history", { nonce });
    return result.ok ? asPromptHistoryResult(result.data) : { entries: [], nonce, olderCursor: "", hasOlder: false };
  }

  // 工作区查询
  async ListWorkspaces(): Promise<{ path: string; name: string; active: boolean }[]> {
    const result = await getJson("/api/workspaces");
    return result.ok ? asWorkspaceView(result.data) : [];
  }
  async PickWorkspace(): Promise<string> {
    const result = await postJson("/api/workspaces/pick", {});
    if (result.ok && result.data && typeof result.data === "object" && "path" in result.data) {
      return asString((result.data as Record<string, unknown>).path, "/workspaces/default");
    }
    return "/workspaces/default";
  }
  async SwitchWorkspace(path: string): Promise<string> {
    const result = await postJson("/api/workspaces/switch", { path });
    if (result.ok && result.data && typeof result.data === "object" && "path" in result.data) {
      return asString((result.data as Record<string, unknown>).path, path);
    }
    return path;
  }
  async RemoveWorkspace(_path: string): Promise<void> {
    await postJson("/api/workspaces/remove", { path: _path });
  }
  async ContextUsage(): Promise<unknown> {
    const result = await getJson("/api/workspaces/context");
    return result.ok ? result.data : { usedTokens: 0, contextWindow: 0, remaining: 0 };
  }
  async ContextUsageForTab(_tabID: string): Promise<unknown> {
    return this.ContextUsage();
  }

  // 余额与任务占位
  async Balance(): Promise<unknown> {
    return { balance: null };
  }
  async BalanceForTab(_tabID: string): Promise<unknown> {
    return this.Balance();
  }
  async Jobs(): Promise<unknown[]> {
    return [];
  }
  async JobsForTab(_tabID: string): Promise<unknown[]> {
    return [];
  }
  async ToolResultForTab(_tabID: string, _toolID: string): Promise<{ args: string; output: string } | null> {
    return null;
  }
  async Meta(): Promise<unknown> {
    return {};
  }
  async MetaForTab(_tabID: string): Promise<unknown> {
    return this.Meta();
  }
  async Commands(): Promise<unknown[]> {
    return [];
  }
  async Capabilities(): Promise<unknown> {
    return { tools: [], mcpServers: [], skills: [] };
  }
  async MCPServers(): Promise<unknown[]> {
    return [];
  }
  async SkillsSettings(): Promise<unknown> {
    return {};
  }
  async AddMCPServer(_input: unknown): Promise<number> {
    return 0;
  }
  async UpdateMCPServer(_name: string, _input: unknown): Promise<void> {
    // no-op
  }
  async RemoveMCPServer(_name: string): Promise<void> {
    // no-op
  }
  async ReconnectMCPServer(_name: string): Promise<void> {
    // no-op
  }
  async ClearMCPServerAuthentication(_name: string): Promise<void> {
    // no-op
  }
  async TrustMCPServerTool(_name: string, _toolName: string): Promise<void> {
    // no-op
  }
  async TrustMCPServerTools(_name: string, _toolNames: string[]): Promise<void> {
    // no-op
  }
  async UntrustMCPServerTool(_name: string, _toolName: string): Promise<void> {
    // no-op
  }
  async PickSkillFolder(): Promise<string> {
    return "";
  }
  async AddSkillPath(_path: string): Promise<void> {
    // no-op
  }
  async RemoveSkillPath(_path: string): Promise<void> {
    // no-op
  }
  async RefreshSkills(): Promise<void> {
    // no-op
  }
  async ReloadCommands(): Promise<void> {
    // no-op
  }
  async SetSkillEnabled(_name: string, _enabled: boolean): Promise<void> {
    // no-op
  }
  async SetMCPServerEnabled(_name: string, _enabled: boolean): Promise<void> {
    // no-op
  }
  async SetMCPServerTier(_name: string, _tier: string): Promise<void> {
    // no-op
  }
  async SlashArgs(input: string): Promise<unknown> {
    return { args: input, result: {} };
  }
  async ListDir(_rel: string): Promise<{ name: string; kind: string; path: string }[]> {
    return [];
  }
  async SearchFileRefs(_query: string): Promise<{ name: string; kind: string; path: string }[]> {
    return [];
  }
  async ReadFile(_rel: string): Promise<{ content: string; language?: string }> {
    return { content: "" };
  }
  async WorkspaceChanges(_tabID: string): Promise<unknown> {
    return { files: [] };
  }
  async GitBranches(): Promise<string[]> {
    return [];
  }
  async GitCheckout(_branch: string): Promise<void> {
    // no-op
  }
  async WorkspaceGitHistory(_tabID: string, _path: string): Promise<unknown[]> {
    return [];
  }
  async WorkspaceGitCommitDetail(_hash: string, _path: string): Promise<unknown> {
    return {};
  }
  async OpenWorkspacePath(_rel: string): Promise<void> {
    // no-op
  }
  async RevealWorkspacePath(_path: string): Promise<void> {
    // no-op
  }
  async RevealPath(_path: string): Promise<void> {
    // no-op
  }
  async SavePastedImage(_dataUrl: string): Promise<string> {
    return "";
  }
  async SaveClipboardImage(): Promise<string> {
    return "";
  }
  async SavePastedFile(_name: string, _dataUrl: string): Promise<string> {
    return "";
  }
  async PickExportFile(_defaultFilename: string, _mimeType: string): Promise<string> {
    return "";
  }
  async SaveExportFile(_path: string, _payload: string, _base64Encoded: boolean): Promise<void> {
    // no-op
  }
  async AttachDropped(_path: string): Promise<unknown> {
    return { path: _path, kind: "file" };
  }
  async AttachmentDataURL(_path: string): Promise<string> {
    return "";
  }
  async Models(): Promise<unknown[]> {
    return [];
  }
  async SetModel(_name: string): Promise<void> {
    this.modelId = _name;
    await postJson("/api/models/default", { model: _name });
  }
  async ModelsForTab(_tabID: string): Promise<unknown[]> {
    return this.Models();
  }
  async SetModelForTab(_tabID: string, name: string): Promise<void> {
    await this.SetModel(name);
  }
  async Effort(): Promise<unknown> {
    return {};
  }
  async SetEffort(_level: string): Promise<void> {
    await postJson("/api/chat/effort", { sessionId: this.sessionId, level: _level });
  }
  async EffortForTab(_tabID: string): Promise<unknown> {
    return this.Effort();
  }
  async SetEffortForTab(_tabID: string, level: string): Promise<void> {
    await this.SetEffort(level);
  }
  async SetTokenMode(mode: string): Promise<void> {
    await postJson("/api/chat/token-mode", { sessionId: this.sessionId, mode });
  }
  async SetTokenModeForTab(_tabID: string, mode: string): Promise<void> {
    await this.SetTokenMode(mode);
  }
  async Memory(): Promise<unknown> {
    return {};
  }
  async MemorySuggestions(): Promise<unknown> {
    return { suggestions: [] };
  }
  async AcceptMemorySuggestion(_suggestion: unknown): Promise<string> {
    return "";
  }
  async AcceptSkillSuggestion(_suggestion: unknown): Promise<string> {
    return "";
  }
  async MemoryForTab(_tabID: string): Promise<unknown> {
    return this.Memory();
  }
  async MemorySuggestionsForTab(_tabID: string): Promise<unknown> {
    return this.MemorySuggestions();
  }
  async AcceptMemorySuggestionForTab(_tabID: string, suggestion: unknown): Promise<string> {
    return this.AcceptMemorySuggestion(suggestion);
  }
  async AcceptSkillSuggestionForTab(_tabID: string, suggestion: unknown): Promise<string> {
    return this.AcceptSkillSuggestion(suggestion);
  }
  async Remember(_scope: string, _note: string): Promise<string> {
    await postJson("/api/chat/memory/remember", { sessionId: this.sessionId, scope: _scope, note: _note });
    return "";
  }
  async RememberForTab(_tabID: string, scope: string, note: string): Promise<string> {
    return this.Remember(scope, note);
  }
  async Forget(_name: string): Promise<void> {
    await postJson("/api/chat/memory/forget", { sessionId: this.sessionId, name: _name });
  }
  async ForgetForTab(_tabID: string, name: string): Promise<void> {
    await this.Forget(name);
  }
  async SaveDoc(_path: string, _body: string): Promise<string> {
    await postJson("/api/chat/doc/save", { sessionId: this.sessionId, path: _path, body: _body });
    return _path;
  }
  async SaveDocForTab(_tabID: string, path: string, body: string): Promise<string> {
    return this.SaveDoc(path, body);
  }
  async DesktopStartupSettings(): Promise<unknown> {
    return {};
  }
  async Settings(): Promise<unknown> {
    return {};
  }
  async HooksSettings(_scope: string): Promise<unknown> {
    return [];
  }
  async SaveHooksSettings(_scope: string, _hooks: unknown[]): Promise<void> {
    // no-op
  }
  async SaveHooksSettingsForRoot(_scope: string, _projectRoot: string, _hooks: unknown[]): Promise<void> {
    // no-op
  }
  async TrustProjectHooks(): Promise<void> {
    // no-op
  }
  async TrustProjectHooksForRoot(_projectRoot: string): Promise<void> {
    // no-op
  }
  async SetDefaultModel(ref: string): Promise<void> {
    await this.SetModel(ref);
  }
  async SetPlannerModel(_ref: string): Promise<void> {
    await postJson("/api/models/planner", { model: _ref });
  }
  async SetSubagentModel(_ref: string): Promise<void> {
    await postJson("/api/models/subagent", { model: _ref });
  }
  async SetSubagentEffort(_level: string): Promise<void> {
    await postJson("/api/chat/effort", { sessionId: this.sessionId, level: _level, scope: "subagent" });
  }
  async SetAutoPlan(_mode: string): Promise<void> {
    await postJson("/api/chat/plan", { sessionId: this.sessionId, mode: _mode });
  }
  async SetDefaultToolApprovalMode(_mode: string): Promise<void> {
    await this.SetToolApprovalMode(_mode);
  }
  async SaveProvider(_provider: unknown): Promise<void> {
    await postJson("/api/models/providers/save", { provider: _provider });
  }
  async AddOfficialProviderAccess(_kind: string, _key: string): Promise<string> {
    await postJson("/api/models/providers/access", { kind: _kind, key: _key });
    return "";
  }
  async FetchProviderModels(_provider: unknown): Promise<unknown[]> {
    return [];
  }
  async DeleteProvider(_name: string): Promise<void> {
    await postJson("/api/models/providers", { name: _name });
  }
  async RemoveProviderAccess(_name: string): Promise<void> {
    await postJson("/api/models/providers/access", { name: _name, remove: true });
  }
  async SetProviderKey(_apiKeyEnv: string, _value: string): Promise<string> {
    await postJson("/api/models/providers/key", { env: _apiKeyEnv, value: _value });
    return _apiKeyEnv;
  }
  async ClearProviderKey(_apiKeyEnv: string): Promise<void> {
    await postJson("/api/models/providers/key", { env: _apiKeyEnv, clear: true });
  }
  async SetPermissionMode(_mode: string): Promise<void> {
    await postJson("/api/chat/permissions", { sessionId: this.sessionId, mode: _mode });
  }
  async AddPermissionRule(_list: string, _rule: string): Promise<void> {
    await postJson("/api/chat/permissions/rules", { sessionId: this.sessionId, list: _list, rule: _rule });
  }
  async RemovePermissionRule(_list: string, _rule: string): Promise<void> {
    await postJson("/api/chat/permissions/rules", { sessionId: this.sessionId, list: _list, rule: _rule, remove: true });
  }
  async SetSandbox(
    _bash: string,
    _network: boolean,
    _workspaceRoot: string,
    _allowWrite: string[],
    _shell: string
  ): Promise<void> {
    await postJson("/api/chat/sandbox", {
      sessionId: this.sessionId,
      bash: _bash,
      network: _network,
      workspaceRoot: _workspaceRoot,
      allowWrite: _allowWrite,
      shell: _shell,
    });
  }
  async SetNetwork(_network: unknown): Promise<void> {
    await postJson("/api/chat/network", { sessionId: this.sessionId, network: _network });
  }
  async SetBotSettings(_settings: unknown): Promise<void> {
    await postJson("/api/bot/settings", { settings: _settings });
  }
  async SetBotConnectionToolApprovalMode(_connID: string, _mode: string): Promise<void> {
    await postJson("/api/bot/connections/approval-mode", { connId: _connID, mode: _mode });
  }
  async SetBotSecret(_envName: string, _value: string): Promise<void> {
    await postJson("/api/bot/secrets", { env: _envName, value: _value });
  }
  async ClearBotSecret(_envName: string): Promise<void> {
    await postJson("/api/bot/secrets", { env: _envName, clear: true });
  }
  async StartBotConnectionInstall(_provider: string, _domain: string): Promise<unknown> {
    return {};
  }
  async PollBotConnectionInstall(_installID: string): Promise<unknown> {
    return {};
  }
  async BotRuntimeStatus(): Promise<unknown> {
    return { running: false, connections: 0, status: "idle" };
  }
  async DiagnoseBotConnection(_id: string): Promise<unknown> {
    return {};
  }
  async TestBotConnection(_id: string, _target?: string): Promise<unknown> {
    return {};
  }
  async SetCloseBehavior(_mode: string): Promise<void> {
    await postJson("/api/desktop/behavior", { close: _mode });
  }
  async SetDisplayMode(_mode: string): Promise<void> {
    await postJson("/api/desktop/display", { mode: _mode });
  }
  async SetStatusBarStyle(_style: string): Promise<void> {
    await postJson("/api/desktop/statusbar", { style: _style });
  }
  async SetStatusBarItems(_items: string[]): Promise<void> {
    await postJson("/api/desktop/statusbar/items", { items: _items });
  }
  async SetDesktopLanguage(_lang: string): Promise<void> {
    await postJson("/api/desktop/language", { lang: _lang });
  }
  async SetDesktopAppearance(_theme: string, _style: string): Promise<void> {
    await postJson("/api/desktop/appearance", { theme: _theme, style: _style });
  }
  async SetDesktopLayoutStyle(_style: string): Promise<void> {
    await postJson("/api/desktop/layout", { style: _style });
  }
  async SetDesktopCheckUpdates(_enabled: boolean): Promise<void> {
    await postJson("/api/desktop/updates", { enabled: _enabled });
  }
  async SetDesktopTelemetry(_enabled: boolean): Promise<void> {
    await postJson("/api/desktop/telemetry", { enabled: _enabled });
  }
  async SetDesktopMetrics(_enabled: boolean): Promise<void> {
    await postJson("/api/desktop/metrics", { enabled: _enabled });
  }
  async SetMemoryCompilerEnabled(_enabled: boolean): Promise<void> {
    await postJson("/api/desktop/memory-compiler", { enabled: _enabled });
  }
  async SetExpandThinking(_on: boolean): Promise<void> {
    await postJson("/api/desktop/expand-thinking", { enabled: _on });
  }
  async MigrateDesktopPreferences(_language: string, _theme: string, _style: string): Promise<void> {
    await postJson("/api/desktop/migrate", { language: _language, theme: _theme, style: _style });
  }
  async SetAgentParams(_temperature: number, _maxSteps: number, _plannerMaxSteps: number, _systemPrompt: string): Promise<void> {
    await postJson("/api/chat/agent-params", {
      sessionId: this.sessionId,
      temperature: _temperature,
      maxSteps: _maxSteps,
      plannerMaxSteps: _plannerMaxSteps,
      systemPrompt: _systemPrompt,
    });
  }
  async SetColdResumePrune(_enabled: boolean): Promise<void> {
    await postJson("/api/chat/cold-resume", { sessionId: this.sessionId, enabled: _enabled });
  }
  async SetReasoningLanguage(_lang: string): Promise<void> {
    await postJson("/api/chat/reasoning-language", { sessionId: this.sessionId, lang: _lang });
  }
  async SetTrayLocale(_locale: "en" | "zh" | "zh-TW"): Promise<void> {
    await postJson("/api/desktop/tray-locale", { locale: _locale });
  }
  async SetBypass(_on: boolean): Promise<void> {
    await postJson("/api/tools/approvals/bypass", { sessionId: this.sessionId, on: _on });
  }
  async Version(): Promise<string> {
    const result = await getJson("/api/desktop/version");
    if (result.ok && result.data && typeof result.data === "object" && "version" in result.data) {
      return asString((result.data as Record<string, unknown>).version, "dev");
    }
    return "dev";
  }
  async CheckUpdate(): Promise<unknown> {
    return null;
  }
  async DownloadUpdate(): Promise<unknown> {
    return null;
  }
  async InstallUpdate(): Promise<void> {
    // no-op
  }
  async ApplyUpdate(): Promise<void> {
    // no-op
  }
  async OpenDownloadPage(): Promise<void> {
    // no-op
  }
  async NeedsOnboarding(): Promise<boolean> {
    const result = await getJson("/api/desktop/onboarding");
    if (result.ok && result.data && typeof result.data === "object" && "needed" in result.data) {
      return Boolean((result.data as Record<string, unknown>).needed);
    }
    return false;
  }
  async ConnectKey(_apiKey: string): Promise<string> {
    const result = await postJson("/api/models/providers/connect", { apiKey: _apiKey });
    if (result.ok && result.data && typeof result.data === "object" && "key" in result.data) {
      return asString((result.data as Record<string, unknown>).key, "");
    }
    return "";
  }
  async ReportCrash(_kind: string, _detail: string): Promise<void> {
    await postJson("/api/desktop/crash-report", { kind: _kind, detail: _detail });
  }
  async ListTabs(): Promise<unknown[]> {
    const result = await getJson("/api/desktop/tabs");
    if (result.ok && Array.isArray(result.data)) {
      return result.data;
    }
    return [];
  }
  async OpenProjectTab(_workspaceRoot: string, _topicID: string): Promise<unknown> {
    const result = await postJson("/api/desktop/tabs/open", { scope: "project", workspaceRoot: _workspaceRoot, topicId: _topicID });
    if (result.ok && result.data && typeof result.data === "object" && "id" in result.data) {
      return result.data;
    }
    return { id: "tab-unknown", topicId: _topicID };
  }
  async OpenGlobalTab(_topicID: string): Promise<unknown> {
    const result = await postJson("/api/desktop/tabs/open", { scope: "global", topicId: _topicID });
    if (result.ok && result.data && typeof result.data === "object" && "id" in result.data) {
      return result.data;
    }
    return { id: "tab-unknown", topicId: _topicID };
  }
  async OpenTopicSession(_scope: string, _workspaceRoot: string, _topicID: string, _sessionPath: string): Promise<unknown> {
    const result = await postJson("/api/desktop/tabs/open", { scope: _scope, workspaceRoot: _workspaceRoot, topicId: _topicID, sessionPath: _sessionPath });
    if (result.ok && result.data && typeof result.data === "object" && "id" in result.data) {
      return result.data;
    }
    return { id: "tab-unknown", topicId: _topicID };
  }
  async EnsureBlankTab(_scope: string, _workspaceRoot: string): Promise<unknown> {
    const result = await postJson("/api/desktop/tabs/blank", { scope: _scope, workspaceRoot: _workspaceRoot });
    if (result.ok && result.data && typeof result.data === "object" && "id" in result.data) {
      return result.data;
    }
    return { id: "tab-blank", topicId: "" };
  }
  async EnsureBlankSurface(_scope: string, _workspaceRoot: string): Promise<unknown> {
    return this.EnsureBlankTab(_scope, _workspaceRoot);
  }
  async SetActiveTab(_tabID: string): Promise<void> {
    await postJson("/api/desktop/tabs/active", { tabId: _tabID });
  }
  async ReorderTabs(_tabIDs: string[]): Promise<void> {
    await postJson("/api/desktop/tabs/reorder", { tabIds: _tabIDs });
  }
  async CloseTab(_tabID: string): Promise<void> {
    await postJson("/api/desktop/tabs/close", { tabId: _tabID });
  }
  async ListProjectTree(): Promise<unknown[]> {
    const result = await getJson("/api/desktop/project-tree");
    return result.ok ? (Array.isArray(result.data) ? (result.data as unknown[]) : []) : [];
  }
  async RenameProject(_workspaceRoot: string, _title: string): Promise<void> {
    await postJson("/api/desktop/projects/rename", { workspaceRoot: _workspaceRoot, title: _title });
  }
  async SetProjectColor(_workspaceRoot: string, _color: string): Promise<void> {
    await postJson("/api/desktop/projects/color", { workspaceRoot: _workspaceRoot, color: _color });
  }
  async SetProjectPinned(_workspaceRoot: string, _pinned: boolean): Promise<void> {
    await postJson("/api/desktop/projects/pin", { workspaceRoot: _workspaceRoot, pinned: _pinned });
  }
  async ReorderProjects(_workspaceRoots: string[]): Promise<void> {
    await postJson("/api/desktop/projects/reorder", { roots: _workspaceRoots });
  }
  async CreateTopic(_scope: string, _workspaceRoot: string, _title: string): Promise<unknown> {
    const result = await postJson("/api/desktop/topics", { scope: _scope, workspaceRoot: _workspaceRoot, title: _title });
    if (result.ok && result.data && typeof result.data === "object" && "id" in result.data) {
      return result.data;
    }
    return { id: "topic-unknown", title: _title };
  }
  async RenameTopic(_topicID: string, _title: string): Promise<void> {
    await postJson("/api/desktop/topics/rename", { topicId: _topicID, title: _title });
  }
  async DeleteTopic(_topicID: string): Promise<void> {
    await postJson("/api/desktop/topics", { topicId: _topicID, remove: true });
  }
  async TrashTopic(_topicID: string): Promise<void> {
    await postJson("/api/desktop/topics", { topicId: _topicID, trash: true });
  }
  async SetTopicPinned(_topicID: string, _pinned: boolean): Promise<void> {
    await postJson("/api/desktop/topics/pin", { topicId: _topicID, pinned: _pinned });
  }
  async ContextPanel(_tabID: string): Promise<unknown> {
    const result = await getJson(`/api/desktop/context-panel?tabId=${encodeURIComponent(_tabID)}`);
    return result.ok ? result.data : {};
  }
  async ActivateTopic(_scope: string, _workspaceRoot: string, _topicID: string, _sessionPath?: string): Promise<void> {
    await postJson("/api/desktop/topics/activate", { scope: _scope, workspaceRoot: _workspaceRoot, topicId: _topicID, sessionPath: _sessionPath });
  }
  async ConfirmAction(_req: { title: string; message: string; detail: string; confirmLabel: string; cancelLabel: string; destructive: boolean }): Promise<boolean> {
    return true;
  }
  async SaveWindowState(_state: unknown): Promise<void> {
    // no-op in browser mode
  }

  // 事件订阅代理
  onEvent(cb: (e: { kind: string; text?: string; reasoning?: string; err?: string; tabId?: string }) => void): () => void {
    return this.eventBus.subscribe("agent", cb);
  }
  onUpdaterProgress(_cb: (p: { phase: string; received: number; total: number; err?: string }) => void): () => void {
    return () => {};
  }
  onReady(_cb: () => void): () => void {
    return () => {};
  }
  onProjectTreeChanged(_cb: () => void): () => void {
    return () => {};
  }
}

async function submitCore(method: string, input: string, sessionId: string, modelId: string | undefined): Promise<void> {
  const body: Record<string, unknown> = { query: input, sessionId, method };
  if (modelId) {
    body.modelId = modelId;
  }
  const result = await postJson("/api/chat/stream", body);
  if (!result.ok) {
    throw new Error(result.error ?? `${method} failed`);
  }
}

export const httpApp = new HttpApp();
