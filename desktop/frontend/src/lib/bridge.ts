// bridge is the single seam between the React app and the Go kernel. In the Wails
// shell it calls the bound App methods (window.go.main.App.*) and subscribes to
// the runtime event stream (window.runtime.EventsOn). In a plain browser (`pnpm
// dev` outside the shell) those globals are absent, so it falls back to a mock
// that streams a canned turn through the same contract — letting the whole UI be
// developed and laid out without rebuilding the Go side.

// @ts-ignore `wails generate module` creates this locally; fresh checkouts keep
// typecheck green by falling back to a disabled drift check below.
import type * as GeneratedApp from "../../wailsjs/go/main/App";

import { addBreadcrumb } from "./breadcrumbs";
import { t } from "./i18n";
import { providerRequiresKey } from "./providerModels";
import { DEFAULT_STATUS_BAR_ITEMS, normalizeStatusBarItems } from "./statusBarItems";
import { modeHasAutoApproveTools, modeWithAutoApproveTools, modeWithPlan, normalizeCollaborationMode, normalizeMode, normalizeTokenMode, normalizeToolApprovalMode } from "./types";

import type {
  BalanceInfo,
  BotConnectionDiagnostic,
  BotInstallPollResult,
  BotInstallStartResult,
  BotRuntimeStatusView,
  BotSettingsView,
  CapabilitiesView,
  CheckpointMeta,
  CommandInfo,
  ContextInfo,
  ContextPanelInfo,
  DirEntry,
  DesktopStartupSettingsView,
  DroppedItem,
  EffortInfo,
  FilePreview,
  HistoryMessage,
  HistoryPage,
  HookConfigView,
  HooksSettingsView,
  JobView,
  MCPServerInput,
  MemorySuggestion,
  MemorySuggestionsView,
  MemoryView,
  Meta,
  Mode,
  ModelInfo,
  NetworkView,
  ProjectNode,
  PromptHistoryEntry,
  PromptHistoryResult,
  ProviderView,
  QuestionAnswer,
  ServerView,
  SessionMeta,
  SettingsView,
  SkillsSettingsView,
  SkillRootView,
  SkillSuggestion,
  SkillView,
  SlashArgsResult,
  TabMeta,
  TopicMeta,
  ToolApprovalMode,
  UpdateDownloadResult,
  UpdateInfo,
  UpdateProgress,
  WireEvent,
  WorkspaceChangesView,
  GitCommitView,
  GitCommitDetailView,
  WorkspaceView,
} from "./types";

const GLOBAL_PROJECT_ORDER_KEY = "__global__";

function stripGoalResearchFlags(arg: string): string {
  const parts = arg.trim().split(/\s+/).filter(Boolean);
  while (parts.length > 0) {
    const flag = parts[0].toLowerCase();
    if (flag !== "--research" && flag !== "--auto-research" && flag !== "--deep" && flag !== "--simple" && flag !== "--no-research") break;
    parts.shift();
  }
  return parts.join(" ");
}

// AppBindings is derived from the Wails-generated Go → TS method signatures, so
// the compiler catches drift between the Go binding surface and the frontend mock.
// Run `wails generate module` after adding/renaming a bound method on App, then
// `pnpm typecheck` to verify the mock still satisfies the contract.
//
// Types for the new native-feel bindings — kept inline since they are
// bridge-specific and only used in AppBindings / the dev mock.
interface NativeConfirmRequest {
  title: string;
  message: string;
  detail: string;
  confirmLabel: string;
  cancelLabel: string;
  destructive: boolean;
}

interface DesktopWindowState {
  width: number;
  height: number;
  x: number;
  y: number;
  maximised: boolean;
}

// AppBindings is the hand-written contract between the React app and the Go
// kernel. It uses local types (types.ts) so components don't import generated
// model classes. _CheckGeneratedBindings catches drift: when a Go method is
// added or renamed, the generated types shift, and a key present in GeneratedApp
// but missing from AppBindings causes a type error here. Fix: add the new method
// to AppBindings, then run `pnpm typecheck` to verify.
export interface AppBindings {
  Platform(): Promise<string>;
  // ── Heartbeat ──
  HeartbeatListTasks(): Promise<unknown>;
  HeartbeatReloadTasks(): Promise<unknown>;
  HeartbeatSaveTasks(tasks: unknown): Promise<void>;
  HeartbeatTriggerNow(id: string): Promise<void>;
  HeartbeatGenerateID(): Promise<string>;
  Submit(input: string): Promise<void>;
  SubmitToTab(tabID: string, input: string): Promise<void>;
  SubmitDisplay(display: string, input: string): Promise<void>;
  SubmitDisplayToTab(tabID: string, display: string, input: string): Promise<void>;
  RunShell(command: string): Promise<void>;
  RunShellForTab(tabID: string, command: string): Promise<void>;
  Steer(text: string): Promise<void>;
  SteerForTab(tabID: string, text: string): Promise<void>;
  Cancel(): Promise<void>;
  CancelTab(tabID: string): Promise<void>;
  Approve(id: string, allow: boolean, session: boolean, persist: boolean): Promise<void>;
  ApproveTab(tabID: string, id: string, allow: boolean, session: boolean, persist: boolean): Promise<void>;
  AnswerQuestion(id: string, answers: QuestionAnswer[]): Promise<void>;
  AnswerQuestionForTab(tabID: string, id: string, answers: QuestionAnswer[]): Promise<void>;
  ReplayPendingPrompts(): Promise<void>;
  SetPlanMode(on: boolean): Promise<void>;
  SetMode(mode: string): Promise<void>;
  SetModeForTab(tabID: string, mode: string): Promise<void>;
  SetAutoApproveTools(on: boolean): Promise<void>;
  SetCollaborationMode(mode: string): Promise<void>;
  SetCollaborationModeForTab(tabID: string, mode: string): Promise<void>;
  SetToolApprovalMode(mode: string): Promise<void>;
  SetToolApprovalModeForTab(tabID: string, mode: string): Promise<void>;
  SetGoal(goal: string): Promise<void>;
  SetGoalForTab(tabID: string, goal: string): Promise<void>;
  ClearGoal(): Promise<void>;
  ClearGoalForTab(tabID: string): Promise<void>;
  Compact(): Promise<void>;
  NewSession(): Promise<void>;
  ClearSession(): Promise<void>;
  History(): Promise<HistoryMessage[]>;
  HistoryForTab(tabID: string): Promise<HistoryMessage[]>;
  HistoryPage(beforeTurn: number, limit: number): Promise<HistoryPage>;
  HistoryPageForTab(tabID: string, beforeTurn: number, limit: number): Promise<HistoryPage>;
  HistoryCheckpointTurnsForTab(tabID: string): Promise<number[]>;
  Checkpoints(): Promise<CheckpointMeta[]>;
  CheckpointsForTab(tabID: string): Promise<CheckpointMeta[]>;
  Rewind(turn: number, scope: string): Promise<void>;
  Fork(turn: number): Promise<TabMeta>;
  SummarizeFrom(turn: number): Promise<void>;
  SummarizeUpTo(turn: number): Promise<void>;
  ListSessions(): Promise<SessionMeta[]>;
  ListTrashedSessions(): Promise<SessionMeta[]>;
  ResumeSession(path: string): Promise<HistoryMessage[]>;
  ResumeSessionForTab(tabID: string, path: string): Promise<HistoryMessage[]>;
  ResumeSessionPage(path: string, limit: number): Promise<HistoryPage>;
  ResumeSessionPageForTab(tabID: string, path: string, limit: number): Promise<HistoryPage>;
  OpenChannelSessionForTab(tabID: string, path: string): Promise<HistoryMessage[]>;
  OpenChannelSessionPageForTab(tabID: string, path: string, limit: number): Promise<HistoryPage>;
  PreviewSession(path: string): Promise<HistoryMessage[]>;
  DeleteSession(path: string): Promise<void>;
  RestoreSession(path: string): Promise<void>;
  PurgeTrashedSession(path: string): Promise<void>;
  RenameSession(path: string, title: string): Promise<void>;
  ScanPromptHistory(nonce: string): Promise<PromptHistoryResult>;
  ListWorkspaces(): Promise<WorkspaceView[]>;
  PickWorkspace(): Promise<string>;
  SwitchWorkspace(path: string): Promise<string>;
  RemoveWorkspace(path: string): Promise<void>;
  ContextUsage(): Promise<ContextInfo>;
  ContextUsageForTab(tabID: string): Promise<ContextInfo>;
  Balance(): Promise<BalanceInfo>;
  BalanceForTab(tabID: string): Promise<BalanceInfo>;
  Jobs(): Promise<JobView[]>;
  JobsForTab(tabID: string): Promise<JobView[]>;
  ToolResultForTab(tabID: string, toolID: string): Promise<{ args: string; output: string } | null>;
  Meta(): Promise<Meta>;
  MetaForTab(tabID: string): Promise<Meta>;
  Commands(): Promise<CommandInfo[]>;
  Capabilities(): Promise<CapabilitiesView>;
  MCPServers(): Promise<ServerView[]>;
  SkillsSettings(): Promise<SkillsSettingsView>;
  AddMCPServer(input: MCPServerInput): Promise<number>;
  UpdateMCPServer(name: string, input: MCPServerInput): Promise<void>;
  RemoveMCPServer(name: string): Promise<void>;
  ReconnectMCPServer(name: string): Promise<void>;
  ClearMCPServerAuthentication(name: string): Promise<void>;
  TrustMCPServerTool(name: string, toolName: string): Promise<void>;
  TrustMCPServerTools(name: string, toolNames: string[]): Promise<void>;
  UntrustMCPServerTool(name: string, toolName: string): Promise<void>;
  PickSkillFolder(): Promise<string>;
  AddSkillPath(path: string): Promise<void>;
  RemoveSkillPath(path: string): Promise<void>;
  RefreshSkills(): Promise<void>;
  ReloadCommands(): Promise<void>;
  SetSkillEnabled(name: string, enabled: boolean): Promise<void>;
  SetMCPServerEnabled(name: string, enabled: boolean): Promise<void>;
  SetMCPServerTier(name: string, tier: string): Promise<void>;
  SlashArgs(input: string): Promise<SlashArgsResult>;
  ListDir(rel: string): Promise<DirEntry[]>;
  SearchFileRefs(query: string): Promise<DirEntry[]>;
  ReadFile(rel: string): Promise<FilePreview>;
  WorkspaceChanges(tabID: string): Promise<WorkspaceChangesView>;
  GitBranches(): Promise<string[]>;
  GitCheckout(branch: string): Promise<void>;
  WorkspaceGitHistory(tabID: string, path: string): Promise<GitCommitView[]>;
  WorkspaceGitCommitDetail(tabID: string, hash: string, path: string): Promise<GitCommitDetailView>;
