# Reasonix Java 复刻 PRD

> 文档版本：v1.0  
> 最后更新：2026-06-27  
> 状态：草案 / 待评审

---

## 1. 项目概述

### 1.1 背景

`Reasonix`（原始仓库：`D:\\IdeaProjects\\reasonix`，Go 语言实现）是一个面向代码任务的 AI Coding Agent，核心设计哲学是：

- **Prefix-Cache-First**：把 system-prompt 前缀（基础提示词 + 工具列表 + 记忆）设计为字节级稳定，充分利用 DeepSeek 等模型的自动前缀缓存，在长对话中保持低成本。
- **Provider-Agnostic**：抽象 `Provider` 层，一套代码同时支持 OpenAI、DeepSeek、Anthropic 等多个供应商，切换供应商只需改配置。
- **Skill-First Playbook**：Agent 的能力通过 `Skill`（Markdown playbook）扩展，内置、项目、用户三层作用域，同名 Skill 优先级覆盖。
- **Tool-Gate Permission**：所有写操作经过 `Previewer + Permission Gate` 两阶段确认，确保用户可控。

本 PRD 的目标是**用 Java 21 + AgentScope-Java2.0 框架 + Spring Boot 4 完全复刻 Reasonix 核心能力**，命名为 `reansonix-java`（仓库路径：`D:\\IdeaProjects\\reansonix-java`）。

### 1.2 参考代码

| 来源 | 路径 | 用途 |
|------|------|------|
| Reasonix 原版（Go）| `D:\\IdeaProjects\\reasonix` | 行为语义参考（Agent 循环、Skill 系统、Tool 注册、Session 压缩）|
| AgentScope-Java 参考实现 | `D:\\IdeaProjects\\agentscope-java`（`main` 分支） | Java 技术栈参考（`HarnessAgent`、`DynamicModelRegistry`、`SkillManager`、多供应商 YAML 配置）|

### 1.3 核心目标

1. **功能等价的 ReAct Agent 执行循环**：支持思考 → 行动 → 观察的迭代，最多 `max_steps` 轮。
2. **供应商-模型两层动态注册**：YAML/Properties 配置驱动，无需修改 Java 代码即可新增模型。
3. **Skill 系统**：支持 AgentScope、OpenAI Agents、Claude Code 等多种 Skill 格式，自动扫描/热加载。
4. **分层记忆**：`AGENTS.md` / `REASONIX.md` → `MEMORY.md` 自动索引 → 向量 RAG。
5. **Tool 权限门**：所有写操作经 Previewer + Permission Gate 双确认。
6. **多模态 Sub-Agent**：`run_as=subagent` 的 Skill 在隔离子循环中执行，仅返回最终答案。
7. **多前端复用同一 Controller**：CLI、HTTP/SSE、Wails Desktop 共享同一 `AgentController`。

---

## 2. 系统架构

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          Frontend Layer                                   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────────────┐  │
│  │   CLI TUI    │  │ HTTP / SSE   │  │  Wails Desktop (JavaFX/Swing) │  │
│  └──────┬───────┘  └──────┬───────┘  └──────────────┬───────────────┘  │
└─────────┼─────────────────┼────────────────────────┼─────────────────┘
          │                 │                        │
          └─────────────────┴────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────────────────┐
│                     AgentController（统一入口）                           │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │  execute(query, sessionId, modelId, options) → AgentResult         │   │
│  │  executeStream(...) → Flux<AgentStreamEvent>                       │   │
│  │  compactSession(sessionId) → CompactedSession                       │   │
│  └───────────────────────────┬──────────────────────────────────────┘   │
└──────────────────────────────┼──────────────────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────────────────┐
│                         Agent Execution Loop                             │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │  ReActLoop                                                         │    │
│  │   1. ComposePrompt()   ← Memory + Skills + Tools + History       │    │
│  │   2. LLMCall()         ← Provider → Model → ChatResponse          │    │
│  │   3. ParseToolCalls()  ← 提取 tool_call / finish_reason           │    │
│  │   4. ExecuteTools()    ← ToolRegistry → ToolResult[]               │    │
│  │   5. PermissionGate()  ← Previewer + User Approval（写操作）        │    │
│  │   6. UpdateHistory()   ← 追加到 Session                           │    │
│  │   7. CheckDone()       ← finish_reason / max_steps 判断            │    │
│  │   8. CompactIfNeeded() ← 超出 compact_ratio 触发压缩              │    │
│  └─────────────────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────────────────┘
        │                              │
        ▼                              ▼
┌──────────────────┐          ┌──────────────────┐
│  Provider Layer  │          │   Skill Layer    │
│  ┌────────────┐  │          │  ┌────────────┐  │
│  │ModelFactory│  │          │  │SkillManager│  │
│  │ModelRegistry│ │          │  │SkillIndex  │  │
│  │ChatModel   │  │          │  │SkillPackage│  │
│  │Embedding   │  │          │  │SkillFormat │  │
│  └────────────┘  │          │  └────────────┘  │
└──────────────────┘          └──────────────────┘
        │
        ▼
┌──────────────────┐
│    Tool Layer    │
│  ┌────────────┐  │
│  │ToolRegistry│  │
│  │Tool        │  │
│  │Previewer   │  │
│  │Permission  │  │
│  │Gate        │  │
│  └────────────┘  │
└──────────────────┘
        │
        ▼
┌──────────────────┐
│  Memory Layer    │
│  ┌────────────┐  │
│  │DocMemory   │  │  ← AGENTS.md / REASONIX.md
│  │AutoMemory  │  │  ← MEMORY.md 索引
│  │VectorStore │  │  ← RAG 向量检索
│  │SessionStore│  │  ← 对话历史持久化
│  └────────────┘  │
└──────────────────┘
```

---

## 3. 核心模块设计

### 3.1 Agent 执行层

| 类名 | 职责 |
|------|------|
| `AgentController` | 统一入口：`execute()` / `executeStream()` / `compactSession()`，CLI、HTTP、Desktop 三方共用 |
| `AgentConfig` | Agent 行为配置：`maxSteps`、`temperature`、`compactRatio`、`autoPlan` 等 |
| `ReActLoop` | 核心迭代器：ComposePrompt → LLMCall → ParseToolCalls → ExecuteTools → PermissionGate → UpdateHistory → CheckDone → CompactIfNeeded |
| `AgentResult` | 执行结果 DTO：`content` / `toolCalls` / `sessionId` / `usage` / `compactTriggered` |
| `Session` | 会话实体：`sessionId` / `history` / `memorySnapshot` / `modelId` / `createdAt` / `updatedAt` |
| `SessionStore` | Session 仓储接口；默认实现 `InMemorySessionStore`，可替换为 Redis/JDBC 实现 |

**关键设计决策**（对齐 Reasonix）：

```
Prompt 前缀 = [SystemPrompt] + [ToolSchemas] + [SkillIndex] + [MemoryPrefix]
↑ 这部分字节级稳定，不随对话轮次变化 → DeepSeek 前缀缓存命中
实际 Prompt  = Prompt 前缀 + [CompactHistory]
```

### 3.2 Provider / 模型层

参考：`agentscope-java-example/src/main/java/…/properties/DynamicModelProperties.java`

| 类名 | 职责 |
|------|------|
| `ProviderProperties` | `@ConfigurationProperties(prefix = "reasonix.provider")` 绑定 suppliers + models 两层配置 |
| `ModelDef` | 单个模型定义：`id` / `supplierId` / `modelName` / `enabled` / `stream` / `effort` |
| `SupplierDef` | 供应商定义：`id` / `providerType` / `baseUrl` / `apiKeyEnv` / `contextWindow` |
| `ModelFactory` | 根据 `ModelDef` 创建 `ChatModel` / `EmbeddingModel` Bean；支持 `openai-compatible` / `anthropic` / `ollama` |
| `ModelRegistry` | 运行时模型注册表；`getModel(id)` / `getModelIds()` / `reload()` |
| `ChatModel`（接口）| 统一对话抽象；实现：`OpenAiChatModelAdapter` / `AnthropicChatModelAdapter` / `OllamaChatModelAdapter` |
| `EmbeddingModel`（接口）| 统一 Embedding 抽象；对接向量数据库（如 PGVector / Milvus / 内存实现）|

**YAML 配置示例**（`application.yml`）：

```yaml
reasonix:
  provider:
    default-model: deepseek-v4-flash
    timeout: 60s
    suppliers:
      - id: deepseek
        provider-type: openai-compatible
        base-url: https://api.deepseek.com/v1
        api-key: ${DEEPSEEK_API_KEY:}
        enabled: true
      - id: anthropic
        provider-type: anthropic
        api-key: ${ANTHROPIC_API_KEY:}
        enabled: true
    models:
      - id: deepseek-v4-flash
        supplier-id: deepseek
        model-name: deepseek-v4-flash
        stream: true
        enabled: true
      - id: claude-opus-4
        supplier-id: anthropic
        model-name: claude-opus-4-8
        stream: false
        enabled: true
```

### 3.3 Skill 系统

参考：`agentscope-java-example` 的 `SkillManager`、`SkillPackage`、`SkillFormatDetector`

| 类名 | 职责 |
|------|------|
| `Skill` | Skill 实体：`name` / `description` / `body` / `scope` / `runAs` / `allowedTools` / `modelOverride` |
| `SkillStore` | Skill 存储接口 |
| `InMemorySkillStore` | 内存 Skill 存储；默认实现 |
| `SkillManager` | Skill 注册中心：扫描目录、去重、按 scope 优先级选择 |
| `SkillIndex` | Skill 索引：仅 `name + description` 进入 Prompt 前缀；`body` 按需加载 |
| `SkillFormat` | 枚举：`AGENTSCOPE` / `AGENTS`（OpenAI） / `CLAUDE` / `COMMANDCODE` / `LINGMA` / `SCRIPT` |
| `SkillFormatDetector` | 根据目录特征自动识别 Skill 格式 |
| `BaseSkillParser` / `AgentScopeSkillParser` / `AgentsSkillParser` | 各格式解析器 |
| `SkillPackage` | Skill 解析结果 DTO：`skillId` / `format` / `skillDir` / `skillFile` / `scripts` / `metadata` |
| `ScriptExecutor` | 脚本执行器：执行 `runAs=script` 的 Skill 脚本，支持超时控制 |
| `DynamicSkillLoader` | 动态 Skill 加载器：定时扫描目录，热更新 Skill Store |

**Skill 作用域优先级**（对齐 Reasonix）：  
`project` > `custom` > `global` > `builtin`

**Skill 目录约定**：

```
<project-root>/
├── .reasonix/skills/          ← Reasonix 原生格式
├── .agents/skills/            ← OpenAI Agents 格式
├── .agent/skills/             ← AgentScope 格式
└── .claude/skills/            ← Claude Code 格式

~/.reasonix/skills/            ← 全局 Skill
```

**SKILL.md Frontmatter 格式**：

```markdown
---
name: my-skill
description: "技能描述"
run_as: inline          # inline | subagent
allowed_tools:          # run_as=subagent 时限制可用工具
  - read_file
  - bash
model: deepseek-v4-pro   # subagent 可选的模型覆盖
effort: high
---

# My Skill

技能正文...
```

### 3.4 Tool 系统

参考：`reasonix/internal/tool/tool.go` + `agentscope-java-example` 的 `ToolController` / `WeatherTool`

| 类名 | 职责 |
|------|------|
| `Tool`（接口）| `name()` / `description()` / `schema()` / `execute(ctx, args)` / `readOnly()` |
| `ToolRegistry` | Tool 注册表：`register()` / `get()` / `listAll()` / `getReadOnly()` |
| `Previewer`（接口）| 写操作的预览接口：`preview(args) → Change`（不落盘）|
| `PermissionGate` | 写操作权限门：调用 Previewer 生成变更摘要 → 呈现给用户 → 用户批准后执行 |
| `BuiltinToolRegistrar` | 内置 Tool 自注册：`bash` / `readFile` / `writeFile` / `editFile` / `multiEdit` / `grep` / `glob` / `ls` / `webFetch` / `todoWrite` / `ask` |
| `ToolExecutionResult` | 工具执行结果：`success` / `output` / `error` / `durationMs` |
| `ToolAuditMiddleware` | 工具审计中间件：记录调用时间、参数、结果（用于调试/审计）|

**内置 Tool 清单**（v1 对齐 Reasonix）：

| Tool 名 | 说明 | ReadOnly |
|---------|------|---------|
| `bash` | 执行 Shell 命令（支持超时、取消）| `false` |
| `read_file` | 读取文件内容 | `true` |
| `write_file` | 写入文件（需 Permission Gate）| `false` |
| `edit_file` | 行级文件编辑（需 Permission Gate）| `false` |
| `multi_edit` | 批量行级编辑（需 Permission Gate）| `false` |
| `grep` | 正则搜索文件内容 | `true` |
| `glob` | 文件名模式匹配 | `true` |
| `ls` | 列出目录内容 | `true` |
| `web_fetch` | 获取网页内容 | `true` |
| `todo_write` | 写入 Todo 状态 | `false` |
| `ask` | 向用户提问（阻塞等待）| `false` |

### 3.5 Memory 层

参考：`reasonix/internal/memory/`

| 类名 | 职责 |
|------|------|
| `MemorySet` | 当前会话加载的全部记忆：`docs` + `store` + `index` + `cwd` + `userDir` |
| `DocMemory` | 分层文档记忆：`REASONIX.md` > `AGENTS.md` > `CLAUDE.md`，项目 > 全局 |
| `AutoMemory` | 自动记忆存储：`MEMORY.md` 索引 + frontmatter 文件 |
| `VectorStore`（接口）| RAG 向量检索接口；默认实现 `InMemoryVectorStore`，可替换为 PGVector / Milvus |
| `EmbeddingService` | 文本 → 向量转换；委托 `EmbeddingModel` |
| `MemoryCompiler` | 记忆编译器：压缩、去重、摘要化（对齐 Reasonix Memory v5）|

**记忆组装顺序**（Prompt 前缀）：

```
[SystemPrompt]
[ToolSchemasJSON]
[SkillIndex: name + description]
[DocMemory: REASONIX.md / AGENTS.md]
[AutoMemory: MEMORY.md index]
[VectorStore: top-k relevant chunks]
[CompactHistory: last N turns]
```

### 3.6 Session / 上下文管理

参考：`reasonix/internal/agent/session.go`（Go）

| 类名 | 职责 |
|------|------|
| `Session` | 会话实体（见 3.1）|
| `SessionStore` | Session 存储接口；支持内存 + Redis 两种实现 |
| `ConversationRegistry` | 会话注册表：按 `conversationId` 管理活跃会话，支持 HTTP 无状态场景 |
| `CompactService` | 会话压缩服务：当 `compactRatio` 达到阈值时触发，保留关键上下文，压缩历史 |
| `UsageTracker` | Token 用量追踪：`promptTokens` / `completionTokens` / `totalTokens` |
| `StreamingEvent` | 流式事件：`START` / `TOKEN` / `TOOL_CALL_START` / `TOOL_CALL_END` / `DONE` / `ERROR` |

### 3.7 配置层

参考：`agentscope-java-example` 的 `application.yml` + `reasonix/REASONIX.md`

采用 Spring Boot `@ConfigurationProperties` + YAML 实现，避免硬编码。

| 配置节点 | 说明 |
|---------|------|
| `reasonix.default-model` | 默认模型 ID |
| `reasonix.agent.max-steps` | 最大执行轮数 |
| `reasonix.agent.temperature` | 采样温度 |
| `reasonix.agent.auto-plan` | 自动规划模式：`off` / `on` |
| `reasonix.agent.compact-ratio` | 会话压缩阈值（0~1） |
| `reasonix.agent.planner-model` | Planner 模型（双模型模式）|
| `reasonix.provider.suppliers` | 供应商列表 |
| `reasonix.provider.models` | 模型定义列表 |
| `reasonix.tools.enabled` | 启用工具列表（空 = 全部）|
| `reasonix.tools.bash-timeout-seconds` | Bash 超时秒数 |
| `reasonix.skills.paths` | 额外 Skill 扫描路径 |
| `reasonix.skills.disabled` | 禁用的 Skill 列表 |
| `reasonix.permissions.mode` | 权限模式：`ask` / `allow` / `deny` |
| `reasonix.permissions.deny` | 显式拒绝规则列表 |
| `reasonix.permissions.allow` | 显式允许规则列表 |
| `reasonix.serve.auth-mode` | HTTP 认证模式：`none` / `token` / `password` |
| `reasonix.subagent.max-parallel` | 最大并发 Sub-Agent 数 |
| `reasonix.subagent.agents` | Sub-Agent 声明列表 |

### 3.8 Sub-Agent 子系统

| 类名 | 职责 |
|------|------|
| `SubagentConfig` | Sub-Agent 配置：`name` / `description` / `tools` / `workspaceMode` / `modelOverride` |
| `SubagentRegistry` | Sub-Agent 注册表；初始化时从 YAML 加载 |
| `SubagentExecutor` | Sub-Agent 执行器：在隔离 Session 中执行，超时控制，结果收敛 |
| `WorkspaceMode` | 枚举：`ISOLATED`（隔离工作区）/ `SHARED`（共享工作区）|

### 3.9 HTTP / 前端层

参考：`agentscope-java-example` 的 `ChatController` / `McpController` / `SkillController`

| Controller | 端点 | 说明 |
|-----------|------|------|
| `ChatController` | `GET /api/chat` | 基础对话（兼容原版 Reasonix REST API）|
| `ChatController` | `GET /api/chat/agent/ask` | Agent 请求，返回 `AgentResult` JSON |
| `ChatController` | `GET /api/chat/rag/enabled` | 强制启用 RAG |
| `ChatController` | `GET /api/chat/rag/disabled` | 强制禁用 RAG |
| `ChatController` | `GET /api/chat/session` | 查询会话状态 |
| `McpController` | `POST /api/mcp/tools` | MCP 工具列表 |
| `McpController` | `POST /api/mcp/call` | MCP 工具调用 |
| `SkillController` | `GET /api/skills` | 列出所有 Skill |
| `SkillController` | `GET /api/skills/{name}` | 获取 Skill 详情 |
| `ToolController` | `GET /api/tools` | 列出所有可用 Tool |
| `ModelAdminController` | `GET /api/admin/models` | 模型管理接口 |
| `SubagentController` | `GET /api/subagents` | 列出 Sub-Agent |
| `MultiModelController` | `POST /api/chat/multi` | 多模型对比接口 |

**流式响应**：通过 `SseEmitter` 或 Spring WebFlux `Flux<ServerSentEvent>` 推送 `AgentStreamEvent`。

---

## 4. 技术选型

| 层级 | 技术选型 | 说明 |
|------|---------|------|
| 语言 / 运行时 | Java 17 / 21 | LTS 版本，充分利用 Virtual Thread（Project Loom）处理并发 Tool 调用 |
| 框架 | Spring Boot 3.x | Web 层、配置绑定、依赖注入 |
| Agent 框架 | AgentScope-Java `agentscope-harness` | `HarnessAgent` 构建、子 Agent 调度、Memory 配置 |
| LLM 调用 | AgentScope-Java 模型抽象 | 基于 `ChatModel` / `EmbeddingModel` 统一接口，自行封装多供应商适配器（OpenAI / DeepSeek / Anthropic / Ollama 等）|
| 向量数据库 | 可选：PGVector / Milvus / InMemory | RAG 层；开发阶段默认内存实现 |
| 配置 | Spring Boot YAML + `@ConfigurationProperties` | 替代 Reasonix 的 TOML 配置 |
| JSON 序列化 | Jackson | Tool Schema / ChatMessage 序列化 |
| 会话存储 | 内存（默认）/ Redis（可选）| Session 持久化 |
| 构建工具 | Maven 3.9+ | 对齐参考项目 |
| 测试 | JUnit 5 + Spring Test | 单元测试 + 集成测试 |

---

## 5. 功能 Roadmap

### Phase 1 — MVP（最小可用产品）

| 序号 | 功能 | 优先级 |
|------|------|--------|
| P1-1 | Spring Boot 3 项目骨架 + AgentScope 集成 | P0 |
| P1-2 | 供应商-模型两层 YAML 配置 + `ModelFactory` / `ModelRegistry` | P0 |
| P1-3 | `ReActLoop` 基础循环（ComposePrompt → LLMCall → 解析 Tool Call → ExecuteTools）| P0 |
| P1-4 | `ChatController` REST 接口（`/api/chat`、`/api/chat/agent/ask`）| P0 |
| P1-5 | `AGENTS.md` / `REASONIX.md` 分层文档记忆 | P0 |
| P1-6 | 基础内置 Tool：`bash` / `read_file` / `write_file` | P1 |
| P1-7 | `PermissionGate`（写操作审批）| P1 |
| P1-8 | 会话压缩（`compactRatio` 触发）| P1 |

### Phase 2 — Skill & 多 Agent

| 序号 | 功能 | 优先级 |
|------|------|--------|
| P2-1 | Skill 格式检测 + AgentScope / OpenAI Agents / Claude Code 解析器 | P0 |
| P2-2 | `SkillManager` + `SkillIndex`（动态扫描 + 热加载）| P0 |
| P2-3 | `runAs=subagent` Skill 隔离子循环 | P1 |
| P2-4 | Sub-Agent YAML 声明 + `SubagentRegistry` | P1 |
| P2-5 | Orchestrator 模式（Router Agent → Sub-Agent）| P2 |
| P2-6 | `runAs=script` Skill + `ScriptExecutor` | P2 |

### Phase 3 — RAG & 记忆增强

| 序号 | 功能 | 优先级 |
|------|------|--------|
| P3-1 | `VectorStore` 接口 + InMemory 实现 | P0 |
| P3-2 | `EmbeddingService` + 文档分片 + 索引 | P1 |
| P3-3 | RAG 检索集成到 ComposePrompt | P1 |
| P3-4 | `MEMORY.md` 自动记忆索引 | P2 |

### Phase 4 — 多前端 & 生产就绪

| 序号 | 功能 | 优先级 |
|------|------|--------|
| P4-1 | SSE 流式响应 `/api/chat/stream` | P1 |
| P4-2 | `reasonix.toml` → YAML 配置迁移脚本 | P2 |
| P4-3 | CLI TUI（Spring Shell / Picocli）| P2 |
| P4-4 | Wails Desktop 集成 | P3 |
| P4-5 | MCP 工具桥接 | P2 |
| P4-6 | 审计日志 + 用量统计 | P2 |
| P4-7 | Docker 镜像 + Helm Chart | P3 |

---

## 6. 核心接口设计

### 6.1 AgentController（统一入口）

```java
package com.reansonix.agent.controller;

public interface AgentController {
    AgentResult execute(String query, String sessionId, @Nullable String modelId,
                        @Nullable AgentOptions options);

    Flux<AgentStreamEvent> executeStream(String query, String sessionId,
                                          @Nullable String modelId,
                                          @Nullable AgentOptions options);

    CompactedSession compactSession(String sessionId);

    List<String> listAvailableModels();
}
```

### 6.2 ReActLoop（核心执行器）

```java
package com.reansonix.agent.loop;

public class ReActLoop {
    public AgentResult execute(Session session, int maxSteps);
}
```

### 6.3 Tool 接口

```java
package com.reansonix.tool;

public interface Tool {
    String name();
    String description();
    JsonNode schema();
    ToolResult execute(Context ctx, JsonNode args);
    boolean readOnly();
}

public interface Previewer {
    Change preview(JsonNode args) throws Exception;
}
```

### 6.4 Skill 接口

```java
package com.reansonix.skill;

public record Skill(
    String name,
    String description,
    String body,
    Scope scope,
    Path path,
    RunAs runAs,               // INLINE | SUBAGENT
    List<String> allowedTools,
    @Nullable String modelOverride,
    @Nullable String effort
) {}

public enum RunAs { INLINE, SUBAGENT }
public enum Scope { PROJECT, CUSTOM, GLOBAL, BUILTIN }
```

### 6.5 Provider 接口

```java
package com.reansonix.provider;

public interface ChatModel {
    ChatResponse generate(List<Message> messages, GenerateOptions options);
    default Flux<ChatChunk> stream(List<Message> messages, GenerateOptions options) {
        throw new UnsupportedOperationException("Streaming not supported");
    }
}

public interface EmbeddingModel {
    float[] embed(String text);
}
```

---

## 7. 关键行为对齐 Reasonix

| Reasonix 行为 | Reansonix-Java 实现方式 |
|--------------|----------------------|
| Prefix-Cache-First Prompt | `PromptComposer` 将稳定前缀（System + ToolSchema + SkillIndex + DocMemory）与可变历史（CompactHistory）分离，稳定前缀跨轮次保持不变 |
| Provider 两层配置（supplier + model）| `DynamicModelProperties` / `ModelFactory`，YAML 驱动，运行时热重载 |
| Skill 三层扫描（project > custom > global）| `SkillManager.discover()` 按 Scope 优先级合并去重 |
| Memory v5 编译器 | `MemoryCompiler`：超阈值时摘要化历史，保留工具调用骨架 |
| Permission Gate | `PermissionGate` 调用 `Previewer` 生成变更摘要 → `ApprovalService` 等待用户确认 → `ToolExecutor` 实际执行 |
| Sub-Agent Skill | `SubagentExecutor`：在独立 Session + Workspace 中执行，超时 40s，结果收敛为最终答案 |
| TOML 配置迁移 | v2 阶段提供 `ReasonixConfigMigrator`：解析 `reasonix.toml` → 等价 `application.yml` |
| MCP 工具桥接 | `McpController` 转发 MCP 工具调用到 `ToolRegistry` |

---

## 8. 非功能性需求

| 类别 | 要求 |
|------|------|
| 性能 | LLM 首字节延迟 < 5s（网络正常时）；Tool 并行批量执行 |
| 可靠性 | Session 异常中断后可从最近 Checkpoint 恢复 |
| 安全性 | API Key 通过环境变量注入，永不出现在日志或响应体中 |
| 可观测性 | 所有 Tool 调用记录审计日志（参数 + 耗时 + 结果摘要）|
| 兼容性 | 兼容 Reasonix `reasonix.toml` 配置格式（YAML 迁移工具）|
| 可扩展性 | Tool / Skill / Provider 均为接口隔离，第三方可通过 SPI 扩展 |

---

## 9. 测试策略

| 测试类型 | 覆盖范围 | 工具 |
|---------|---------|------|
| 单元测试 | `ReActLoop`、`ToolRegistry`、`SkillManager`、`ModelFactory` | JUnit 5 |
| 集成测试 | `AgentController` REST 接口 + 完整 ReAct 链路 | `@SpringBootTest` + `@WebMvcTest` |
| 契约测试 | ChatModel Provider 适配器（OpenAI / Anthropic / Ollama）| WireMock |
| 回归基准 | 上下文压缩正确性（对齐 Reasonix benchmarks） | JUnit 基准套件 |
| E2E | 端到端对话流程（参照 Reasonix `cmd/e2ebench`）| Testcontainers |

---

## 10. 项目结构

```
reansonix-java/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/reansonix/
│   │   │       ├── agent/
│   │   │       │   ├── AgentController.java       ← 统一入口
│   │   │       │   ├── AgentConfig.java           ← Agent 行为配置
│   │   │       │   ├── ReActLoop.java             ← 核心执行循环
│   │   │       │   ├── AgentResult.java           ← 结果 DTO
│   │   │       │   ├── Session.java               ← 会话实体
│   │   │       │   ├── SessionStore.java          ← 会话存储接口
│   │   │       │   ├── CompactService.java        ← 会话压缩
│   │   │       │   └── AgentStreamEvent.java      ← 流式事件
│   │   │       ├── provider/
│   │   │       │   ├── ChatModel.java             ← 对话模型接口
│   │   │       │   ├── EmbeddingModel.java        ← Embedding 接口
│   │   │       │   ├── ModelFactory.java          ← 模型工厂
│   │   │       │   ├── ModelRegistry.java         ← 模型注册表
│   │   │       │   ├── OpenAiChatModelAdapter.java
│   │   │       │   ├── AnthropicChatModelAdapter.java
│   │   │       │   └── OllamaChatModelAdapter.java
│   │   │       ├── skill/
│   │   │       │   ├── Skill.java                 ← Skill 实体
│   │   │       │   ├── SkillStore.java            ← Skill 存储接口
│   │   │       │   ├── SkillManager.java          ← Skill 注册中心
│   │   │       │   ├── SkillIndex.java            ← Skill 索引
│   │   │       │   ├── SkillFormat.java           ← 格式枚举
│   │   │       │   ├── SkillFormatDetector.java   ← 格式检测器
│   │   │       │   ├── BaseSkillParser.java       ← 解析器基类
│   │   │       │   ├── AgentScopeSkillParser.java
│   │   │       │   ├── AgentsSkillParser.java
│   │   │       │   ├── SkillPackage.java          ← 解析结果 DTO
│   │   │       │   ├── ScriptExecutor.java        ← 脚本执行器
│   │   │       │   └── DynamicSkillLoader.java    ← 动态加载器
│   │   │       ├── tool/
│   │   │       │   ├── Tool.java                  ← Tool 接口
│   │   │       │   ├── ToolRegistry.java          ← Tool 注册表
│   │   │       │   ├── Previewer.java             ← 预览接口
│   │   │       │   ├── PermissionGate.java        ← 权限门
│   │   │       │   ├── ToolResult.java            ← 执行结果
│   │   │       │   ├── builtin/
│   │   │       │   │   ├── BashTool.java
│   │   │       │   │   ├── ReadFileTool.java
│   │   │       │   │   ├── WriteFileTool.java
│   │   │       │   │   ├── EditFileTool.java
│   │   │       │   │   ├── MultiEditTool.java
│   │   │       │   │   ├── GrepTool.java
│   │   │       │   │   ├── GlobTool.java
│   │   │       │   │   ├── LsTool.java
│   │   │       │   │   ├── WebFetchTool.java
│   │   │       │   │   ├── TodoWriteTool.java
│   │   │       │   │   └── AskTool.java
│   │   │       │   └── ToolAuditMiddleware.java
│   │   │       ├── memory/
│   │   │       │   ├── MemorySet.java             ← 记忆全集
│   │   │       │   ├── DocMemory.java             ← 分层文档记忆
│   │   │       │   ├── AutoMemory.java            ← 自动记忆存储
│   │   │       │   ├── VectorStore.java           ← 向量存储接口
│   │   │       │   ├── InMemoryVectorStore.java
│   │   │       │   ├── EmbeddingService.java
│   │   │       │   └── MemoryCompiler.java        ← 记忆编译器
│   │   │       ├── subagent/
│   │   │       │   ├── SubagentConfig.java
│   │   │       │   ├── SubagentRegistry.java
│   │   │       │   ├── SubagentExecutor.java
│   │   │       │   └── WorkspaceMode.java
│   │   │       ├── config/
│   │   │       │   ├── ReasonixConfig.java        ← 根配置
│   │   │       │   ├── ProviderProperties.java    ← 供应商配置
│   │   │       │   ├── AgentProperties.java       ← Agent 配置
│   │   │       │   ├── ToolsProperties.java       ← Tool 配置
│   │   │       │   ├── SkillsProperties.java      ← Skill 配置
│   │   │       │   └── PermissionProperties.java  ← 权限配置
│   │   │       ├── common/
│   │   │       │   └── TraceIdFilter.java
│   │   │       └── ReansonixApplication.java      ← 启动类
│   │   └── resources/
│   │       ├── application.yml                    ← 主配置文件
│   │       └── workspace/                         ← 默认工作区
│   └── test/
│       └── java/
│           └── com/reansonix/
│               ├── agent/ReActLoopTest.java
│               ├── skill/SkillManagerTest.java
│               └── provider/DynamicModelPropertiesBindingTest.java
└── docs/
    ├── PRD.md                  ← 本文档
    ├── ARCHITECTURE.md
    ├── API.md
    └── SKILL_FORMAT.md
```

---

## 11. 与参考项目的映射关系

| Reasonix（Go） | Reansonix-Java（Java） | AgentScope-Java 参考 |
|--------------|----------------------|---------------------|
| `internal/agent/agent.go` | `agent/ReActLoop.java` | `HarnessAgent` |
| `internal/skill/skill.go` | `skill/SkillManager.java` | `SkillManager` |
| `internal/tool/tool.go` | `tool/Tool.java` + `builtin/` | `Toolkit` |
| `internal/provider/` | `provider/` | `ModelFactory` |
| `internal/memory/` | `memory/` | `MemoryConfig` |
| `internal/config/` | `config/ReasonixConfig.java` | `DynamicModelProperties` |
| `internal/control/` | `agent/AgentController.java` | `ChatController` |
| `reasonix.toml` | `application.yml` | `application.yml` |
| `internal/agent/ask.go` | `tool/builtin/AskTool.java` | — |
| `internal/agent/subagent_store.go` | `subagent/SubagentRegistry.java` | `SubagentDeclaration` |

---

## 12. 里程碑与交付物

| 里程碑 | 交付物 | 验收标准 |
|--------|-------|---------|
| M1：骨架 + 模型层 | `pom.xml`、配置类、`ModelFactory` / `ModelRegistry` | `application.yml` 新增模型无需改 Java 代码 |
| M2：ReAct 循环 + REST | `ReActLoop`、`ChatController` | `GET /api/chat/agent/ask?question=xxx` 返回正确 `AgentResult` |
| M3：Skill 系统 | `SkillManager`、3 种格式解析器、动态扫描 | `GET /api/skills` 列出所有已加载 Skill |
| M4：Memory + 压缩 | `MemorySet`、`CompactService`、`VectorStore` 接口 | 超长对话自动压缩，关键上下文不丢失 |
| M5：Sub-Agent | `SubagentExecutor`、YAML 声明加载 | `runAs=subagent` 的 Skill 隔离执行 |
| M6：生产就绪 | SSE 流式、权限门、审计日志 | `GET /api/chat/stream` 正确推送流式事件 |

---

*

---

## 13. Web 支持

### 13.1 目标

复刻 Reasonix Web UI 的核心能力，让用户在浏览器中直接与 Agent 交互。
Web 层共享与 CLI / Desktop 完全一致的 AgentController，不做业务逻辑分支。

### 13.2 架构分层

`
┌─────────────────────────────────────────────────────────────┐
│                        Web Frontend                          │
│  ┌─────────────┐  ┌─────────────┐  ┌──────────────────┐   │
│  │ Chat Panel  │  │ Approval    │  │ Skill / Model     │   │
│  │ (SSE)       │  │ Card        │  │ Admin Panel       │   │
│  └──────┬──────┘  └──────┬──────┘  └────────┬─────────┘   │
└─────────┼────────────────┼─────────────────┼─────────────┘
          │                │                 │
          └────────────────┴─────────────────┘
                            │
┌───────────────────────────▼────────────────────────────────┐
│              WebController（REST + SSE 接口层）                │
│  ┌────────────────────────────────────────────────────────┐ │
│  │ GET  /api/chat                       ← 基础对话（JSON）  │ │
│  │ GET  /api/chat/agent/ask             ← Agent 请求       │ │
│  │ GET  /api/chat/rag/enabled           ← 强制 RAG         │ │
│  │ GET  /api/chat/rag/disabled          ← 禁用 RAG         │ │
│  │ GET  /api/chat/session               ← 会话状态          │ │
│  │ GET  /api/chat/stream                ← SSE 流式         │ │
│  │ POST /api/mcp/tools                  ← MCP 工具列表     │ │
│  │ POST /api/mcp/call                   ← MCP 工具调用     │ │
│  │ GET  /api/skills                     ← Skill 列表       │ │
│  │ GET  /api/tools                      ← Tool 列表        │ │
│  │ GET  /api/admin/models               ← 模型管理         │ │
│  └────────────────────────────────────────────────────────┘ │
└───────────────────────────────────────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────────┐
│              AgentController（共享核心）                         │
└───────────────────────────────────────────────────────────────┘
`

### 13.3 REST API 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/chat | 基础对话；兼容 Reasonix REST API |
| GET | /api/chat/agent/ask?question=… | Agent 请求，返回 AgentResult JSON |
| GET | /api/chat/rag/enabled | 强制启用 RAG |
| GET | /api/chat/rag/disabled | 强制禁用 RAG |
| GET | /api/chat/session?conversationId=… | 查询会话存在性 |
| GET | /api/chat/stream?question=… | SSE 流式响应，推送 AgentStreamEvent |

### 13.4 SSE 流式事件协议

前端通过 EventSource 连接 /api/chat/stream，事件类型如下：

| Event Type | Payload 字段 | 说明 |
|-----------|-------------|------|
| start | sessionId、modelId | 流开始，携带会话和模型标识 |
| 	oken | content | 单个文本 token |
| 	ool_call_start | 	oolName、rguments | 工具调用开始 |
| 	ool_call_end | 	oolName、output | 工具调用完成 |
| done | content、usage、compactTriggered | 流结束，携带最终结果 |
| error | message、code | 错误信息 |

### 13.5 认证与安全

通过 easonix.serve.auth-mode 配置：

| 值 | 行为 |
|----|------|
| 
one | 开放访问（仅限本地开发）|
| 	oken | URL 参数 ?token=… 或 HTTP Header 校验 |
| password | bcrypt-hashed 密码，通过 easonix serve --hash-password 生成 |

Authorization header 向后端 WAF 或反向代理透传；API Key 永远不出现在响应体中。

### 13.6 Web 前端技术选型

| 层级 | 选型 | 说明 |
|------|------|------|
| 框架 | React 18 + Vite 6 | 轻量启动、HMR 开发体验 |
| 状态管理 | Zustand | 会话状态、审批卡片状态 |
| 网络 | native EventSource + etch | SSE 流式 + REST |
| 样式 | Tailwind CSS 3 | 与 Reasonix Web UI 视觉一致 |
| Markdown | eact-markdown | Agent 回答的 Markdown 渲染 |

### 13.7 前端包结构

`
reansonix-java/
└── web/
    ├── src/
    │   ├── App.tsx                ← 主路由
    │   ├── pages/
    │   │   ├── ChatPage.tsx       ← 对话页面（SSE + Markdown）
    │   │   ├── ApprovalPage.tsx   ← Permission Gate 审批卡片
    │   │   ├── SkillsPage.tsx     ← Skill 列表 / 详情
    │   │   └── AdminPage.tsx      ← 模型管理 / 用量统计
    │   ├── api/
    │   │   └── chat.ts            ← fetch + EventSource 封装
    │   ├── components/
    │   │   ├── ChatInput.tsx
    │   │   ├── MessageList.tsx
    │   │   ├── ToolCallPanel.tsx
    │   │   └── ApprovalCard.tsx
    │   └── stores/
    │       └── chatStore.ts
    ├── public/
    │   └── logo.svg
    ├── index.html
    ├── package.json
    ├── vite.config.ts
    └── tailwind.config.ts
`

### 13.8 与 Reasonix Web UI 的兼容性

| Reasonix Web 特性 | Reansonix-Java 实现 |
|------------------|---------------------|
| 流式 token 显示 | SSE 	oken 事件逐字渲染 |
| 工具调用骨架面板 | SSE 	ool_call_start/end 事件展开 |
| Approval 卡片 | 写操作触发 ApprovalCard；用户批准后调确认接口 |
| Goal / Todo 侧栏 | GET /api/chat/todos（Phase 4 补充）|
| 模型切换 | ModelAdminController 下拉选择 |


---

## 14. Desktop 支持

### 14.1 目标

复刻 Reasonix Desktop（Tauri + React）的核心体验，提供本地原生窗口、系统通知、全局快捷键和本地文件系统访问。
Desktop 应用复用与 Web 共享的 AgentController，后端逻辑不做任何平台分支。

### 14.2 技术选型

| 层级 | 选型 | 说明 |
|------|------|------|
| 桌面框架 | **Tauri 2**（Rust 后端 + Web 前端）| 与 Reasonix 原版一致；小体积、安全沙箱 |
| 前端复用 | 与 web/ 共享同一套 React 组件 | web/src/pages/* 在 desktop 中直接引用 |
| 后端桥接 | Tauri invoke() → http://localhost:8787 | Desktop 通过 HTTP 调用同一 Spring Boot 后端 |
| 系统通知 | Tauri Notification API | 工具审批等待、任务完成推送 |
| 全局快捷键 | Tauri Global Shortcut | 快速唤醒窗口（如 CmdOrCtrl+Shift+R）|
| 本地文件 | Tauri s plugin | 选择工作目录、打开文件对话框 |

### 14.3 架构分层

`
┌─────────────────────────────────────────────────────────────┐
│                    Desktop (Tauri 2)                         │
│  ┌────────────────────────────────────────────────────────┐ │
│  │  Rust Main Process                                      │ │
│  │    ├── System Tray                                       │ │
│  │    ├── Global Shortcuts                                  │ │
│  │    ├── Native Notifications                             │ │
│  │    └── File Dialogs (fs plugin)                          │ │
│  └────────────────────────────┬───────────────────────────┘ │
│                               │ invoke("chat", …)             │
└───────────────────────────────┼───────────────────────────────┘
                                 │ HTTP (localhost:8787)
┌────────────────────────────────▼────────────────────────────┐
│              Spring Boot 3 Backend                           │
│  (与 web/ 完全共享同一套 Controller 和 Service)                │
└───────────────────────────────────────────────────────────────┘
`

### 14.4 Tauri 目录结构

`
reansonix-java/
└── desktop/
    ├── src-tauri/
    │   ├── src/
    │   │   ├── main.rs        ← Tauri 入口
    │   │   ├── commands.rs    ← Tauri Commands（代理 HTTP 调用）
    │   │   └── tray.rs        ← 系统托盘
    │   ├── Cargo.toml
    │   ├── tauri.conf.json   ← 窗口尺寸、权限、快捷键配置
    │   └── build.rs
    └── package.json          ← (仅在构建时使用，与 web 独立)
`

### 14.5 Tauri Commands 设计

Tauri Commands 是 Rust 端暴露给前端的薄代理，每个 Command 转发一个 HTTP 请求到 Spring Boot 后端，处理错误并标准化返回格式：

| Tauri Command | 对应 REST 端点 | 说明 |
|---------------|---------------|------|
| chat(message, sessionId, modelId) | GET /api/chat/agent/ask | 单轮对话 |
| chat_stream(message, sessionId, modelId) | GET /api/chat/stream | 流式对话 |
| pprove_tool(toolCallId, approved) | POST /api/chat/approve | 审批工具调用 |
| list_models() | GET /api/admin/models | 模型列表 |
| list_skills() | GET /api/skills | Skill 列表 |

### 14.6 窗口与系统托盘

- **默认窗口**：width: 900px、height: 680px，esizable: true，	itle: "Reansonix"。
- **系统托盘**：启动后最小化到托盘；单击托盘图标恢复窗口；右键菜单含「新建会话」「退出」。
- **全局快捷键**：CmdOrCtrl+Shift+R 唤起/隐藏窗口；CmdOrCtrl+Enter 发送消息。

### 14.7 桌面通知

| 触发时机 | 通知内容 |
|---------|---------|
| 工具审批等待 | 🔔「Reansonix 等待审批：edit_file src/Main.java」|
| 任务完成 | ✅「任务完成，耗时 12.3s」|
| 错误 | ❌「执行失败：模型连接超时」|

### 14.8 打包与分发

| 平台 | 命令 | 产物 |
|------|------|------|
| macOS (arm64) | cargo tauri build --target aarch64-apple-darwin | .app + .dmg |
| Windows (amd64) | cargo tauri build --target x86_64-pc-windows-msvc | .exe + .msi |
| Linux (amd64) | cargo tauri build --target x86_64-unknown-linux-gnu | .AppImage + .deb |

构建产物通过 cargo tauri build 自动签名（macOS）和公证（Windows）；内部测试可设置 TAURI_SKIP_SIGNING=1 跳过。

### 14.9 与 Reasonix Desktop 的兼容性

| Reasonix Desktop 特性 | Reansonix-Java Desktop 实现 |
|---------------------|----------------------------|
| 左侧会话列表面板 | 共享 ConversationRegistry，Web 和 Desktop 看到同一会话列表 |
| 工具调用时间线 | 复用 ToolAuditMiddleware 的审计数据，Desktop 前端渲染调用历史 |
| Approval 卡片弹窗 | Tauri 原生弹窗 + Web 组件双重实现，确保无 WebView 时也能审批 |
| 深色/浅色主题 | TAURI_APP_THEME 环境变量 + CSS prefers-color-scheme 媒体查询 |


---

## 15. Web + Desktop 联合开发指南

### 15.1 环境要求

`
# 后端
java 17+   # JDK
mvn 3.9+   # Maven

# Web 前端
node 20+   # Node.js
pnpm 9+    # 包管理

# Desktop
rust 1.75+ # Rust toolchain
cargo      # 随 rustup 安装
`

### 15.2 本地开发流程

`ash
# 终端 1：启动后端
mvn spring-boot:run

# 终端 2：启动 Web 前端（Vite dev server，代理 /api 到 localhost:8080）
cd web && pnpm install && pnpm dev

# 终端 3：启动 Desktop（Tauri dev，自动连接本地后端）
cd desktop && cargo tauri dev
`

Web 和 Desktop 共享 /api/* REST 端点；两个前端可以同时连接 http://localhost:8080，会话通过 conversationId 隔离。

### 15.3 环境变量

`ash
export DEEPSEEK_API_KEY=sk-...      # 后端 LLM 调用
export REASONIX_THEME=dark          # Web + Desktop 主题
`

### 15.4 Web + Desktop 测试矩阵

| 场景 | 测试命令 |
|------|---------|
| REST 接口 | mvn clean verify（含 @WebMvcTest）|
| Web 前端 E2E | cd web && pnpm test:e2e（Playwright）|
| Desktop E2E | cd desktop && cargo tauri test（Tauri Test Driver）|
| 流式 SSE | curl -N http://localhost:8080/api/chat/stream?question=hello |


---

*本文档由 AgentScope-Java 框架复刻 Reasonix 的产物，仅作 PRD 文档，不含实现代码。*