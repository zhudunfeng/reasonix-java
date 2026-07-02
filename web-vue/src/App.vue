<template>
  <div class="chat-layout">
    <!-- 顶部栏 -->
    <header class="chat-header">
      <div class="chat-header__left">
        <!-- 侧边栏开关 -->
        <button class="icon-btn" type="button" title="切换侧边栏" @click="sidebarOpen = !sidebarOpen">
          <PanelLeft :size="18" />
        </button>
        <span class="chat-header__app-name">Reasonix</span>
        <span v-if="sessionTitle" class="chat-header__session">{{ sessionTitle }}</span>
      </div>
      <div class="chat-header__actions">
        <button class="icon-btn" type="button" title="清空对话" @click="clearChat">
          <Trash2 :size="16" />
        </button>
      </div>
    </header>

    <div class="chat-body">
      <!-- 侧边栏：Session 列表 -->
      <aside v-if="sidebarOpen" class="sidebar">
        <div class="sidebar__header">
          <span class="sidebar__title">对话</span>
          <button class="icon-btn" type="button" title="新建对话" @click="newSession">
            <SquarePen :size="15" />
          </button>
        </div>
        <div class="sidebar__list">
          <div
            v-for="s in sessions"
            :key="s.id"
            :class="['sidebar__item', { 'sidebar__item--active': s.id === currentSessionId }]"
            @click="switchSession(s.id)"
            :title="s.title"
          >
            <MessageSquare :size="14" />
            <span class="sidebar__item-text">{{ s.title || '新对话' }}</span>
            <span class="sidebar__item-meta">{{ s.time }}</span>
          </div>
        </div>
      </aside>

      <!-- 主聊天区 -->
      <main class="chat-main" ref="mainEl">
        <div class="messages" ref="messagesEl">
          <!-- 欢迎页 -->
          <div v-if="items.length === 0" class="welcome">
            <div class="welcome__icon"><Brain :size="32" /></div>
            <h2 class="welcome__title">Reasonix</h2>
            <p class="welcome__sub">AI 编程助手，输入任意问题开始对话</p>
          </div>

          <!-- 消息列表 -->
          <div
            v-for="item in items"
            :key="item.id"
            :class="['message', item.role === 'user' ? 'message--user' : 'message--assistant']"
          >
            <!-- role 标签 -->
            <span v-if="item.role === 'assistant'" class="message__role-label">assistant</span>

            <template v-if="item.role === 'assistant'">
              <!-- ── 思考过程（可折叠）── -->
              <ReasoningBlock
                v-if="item.reasoning"
                :reasoning="item.reasoning"
                :open="item.reasoningOpen"
                :running="item.streaming && !item.reasoningComplete"
                @toggle="item.reasoningOpen = !item.reasoningOpen"
              />

              <!-- ── 工具调用卡片（可折叠）── -->
              <div v-if="item.toolCalls && item.toolCalls.length" class="tool-calls">
                <button
                  v-if="!item.toolCallsOpen"
                  class="tool__head"
                  type="button"
                  @click="item.toolCallsOpen = true"
                >
                  <Wrench :size="14" class="tool__icon" />
                  <span class="tool__name">工具调用</span>
                  <span class="tool__meta">{{ item.toolCalls.length }} 项</span>
                  <ChevronRight :size="13" class="tool__chevron" />
                </button>
                <template v-else>
                  <div class="tool-calls__header-row">
                    <button class="tool__head tool__head--compact" type="button" @click="item.toolCallsOpen = false">
                      <Wrench :size="14" class="tool__icon" />
                      <span class="tool__name">工具调用</span>
                      <span class="tool__meta">{{ item.toolCalls.length }} 项</span>
                      <ChevronRight :size="13" class="tool__chevron tool__chevron--open" />
                    </button>
                  </div>
                  <div class="tool-calls__body">
                    <div v-for="(tool, idx) in item.toolCalls" :key="idx" class="tool__row">
                      <Wrench :size="13" class="tool__icon" />
                      <span class="tool__name">{{ tool.name }}</span>
                      <pre class="tool__args">{{ tool.arguments }}</pre>
                    </div>
                  </div>
                </template>
              </div>

              <!-- ── 工具执行结果 ── -->
              <div v-if="item.toolResults && item.toolResults.length" class="tool-results">
                <div v-for="(result, idx) in item.toolResults" :key="idx" class="tool-result">
                  {{ result }}
                </div>
              </div>

              <!-- ── 助手回复正文 ── -->
              <div v-if="item.content" class="message__body md" v-html="renderMarkdown(item.content)"></div>
            </template>

            <!-- 用户消息气泡 -->
            <template v-else>
              <div class="message__bubble">{{ item.content }}</div>
            </template>
          </div>

          <!-- 流式 loading 指示 -->
          <div v-if="loading" class="message message--assistant">
            <span class="message__role-label">assistant</span>
            <div class="reasoning">
              <div class="reasoning__head" data-running>
                <BrainCircuit :size="13" class="reasoning__icon" />
                <span class="reasoning__label">思考中</span>
                <span class="reasoning__meta">…</span>
              </div>
            </div>
          </div>
        </div>
      </main>
    </div>

    <!-- 底部输入栏 -->
    <footer class="chat-footer">
      <form class="composer" @submit.prevent="send">
        <textarea
          v-model="input"
          class="composer__input"
          placeholder="输入消息，Enter 发送，Shift+Enter 换行…"
          :disabled="loading"
          rows="1"
          @keydown.enter.exact.prevent="send"
          @keydown.enter.shift.exact.prevent="insertNewline"
          @input="autoResize"
          ref="inputEl"
        ></textarea>
        <button class="composer__send" type="submit" :disabled="loading || !input.trim()">
          发送
        </button>
      </form>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue';
import gsap from 'gsap';
import { ScrollToPlugin } from 'gsap/ScrollToPlugin';
import { marked } from 'marked';
import type { MarkedOptions } from 'marked';
// 图标组件（使用 lucide-vue 0.5.x 的命名导出）
import {
  PanelLeft,
  SquarePen,
  Trash2,
  MessageSquare,
  Brain,
  ChevronRight,
  Wrench,
  BrainCircuit,
} from 'lucide-vue-next';

gsap.registerPlugin(ScrollToPlugin);

// ── Markdown 渲染 ────────────────────────────────────────────────────
const MARKED_OPTS: MarkedOptions = { breaks: true, gfm: true };

/** 解析 Markdown 为 HTML */
function renderMarkdown(text: string): string {
  try {
    return marked.parse(text, MARKED_OPTS) as string;
  } catch {
    return text;
  }
}

// ── 类型定义 ─────────────────────────────────────────────────────────
type Role = 'user' | 'assistant';

type ToolCall = { name: string; arguments: string };

type Message = {
  id: string;
  role: Role;
  content: string;
  /** 思考过程文本 */
  reasoning?: string;
  /** 思考过程是否展开 */
  reasoningOpen?: boolean;
  /** 思考过程是否完成（完成后自动折叠） */
  reasoningComplete?: boolean;
  /** 是否正在流式传输 */
  streaming?: boolean;
  /** 工具调用列表 */
  toolCalls?: ToolCall[];
  /** 工具调用是否展开 */
  toolCallsOpen?: boolean;
  /** 工具执行结果 */
  toolResults?: string[];
};

type Session = {
  id: string;
  title: string;
  time: string;
};

// ── Refs ─────────────────────────────────────────────────────────────
const input = ref('');
const loading = ref(false);
const sidebarOpen = ref(true);
const sessions = ref<Session[]>([
  { id: 'default', title: '当前对话', time: '刚刚' },
]);
const currentSessionId = ref('default');
const sessionTitle = ref('Reasonix Chat');

const items = ref<Message[]>([]);
const mainEl = ref<HTMLElement | null>(null);
const messagesEl = ref<HTMLDivElement | null>(null);
const inputEl = ref<HTMLTextAreaElement | null>(null);
/** 当前活跃的 EventSource 连接 */
const currentEventSource = ref<EventSource | null>(null);
/** 当前正在流式填充的 assistant 消息 id */
const currentResponseId = ref<string | null>(null);

const SSE_URL = '/api/chat/stream';

// ── 发送消息 ─────────────────────────────────────────────────────────
async function send() {
  const text = input.value.trim();
  if (!text || loading.value) return;

  const sessionId = currentSessionId.value || 'default';

  // 追加用户消息
  items.value.push({ id: randomId(), role: 'user', content: text });
  input.value = '';
  loading.value = true;
  scrollToBottom(true);

  // 关闭上一次连接
  if (currentEventSource.value) {
    currentEventSource.value.close();
  }

  const es = new EventSource(
    `${SSE_URL}?question=${encodeURIComponent(text)}&sessionId=${encodeURIComponent(sessionId)}`
  );
  currentEventSource.value = es;

  es.addEventListener('message', (rawEvent: MessageEvent) => {
    try {
      const data = JSON.parse(rawEvent.data) as {
        type?: string;
        content?: string;
        errorMessage?: string;
        toolName?: string;
        arguments?: Record<string, unknown>;
        toolResult?: string;
      };
      if (!data) return;
      const kind = String(data.type ?? '').toLowerCase();
      if (!kind) return;

      // 'start' → 创建新的 assistant 响应框
      if (kind === 'start') {
        const last = items.value[items.value.length - 1];
        if (last && last.role === 'assistant' && !last.content && !last.reasoning && !last.toolCalls?.length) {
          items.value.pop();
        }
        const id = randomId();
        items.value.push({
          id,
          role: 'assistant',
          content: '',
          reasoningOpen: false,
          toolCallsOpen: false,
          streaming: true,
          reasoningComplete: false,
        });
        currentResponseId.value = id;
        scrollToBottom(true);
        return;
      }

      if (!currentResponseId.value) return;
      const id = currentResponseId.value;

      if (kind === 'error') {
        appendAssistantText(id, `\n[错误] ${data.errorMessage ?? '未知错误'}`);
      } else if (kind === 'think') {
        appendAssistantReasoning(id, data.content ?? '');
      } else if (kind === 'chunk') {
        appendAssistantText(id, data.content ?? '');
        collapseReasoning(id);
      } else if (kind === 'tool_call') {
        appendToolCall(id, data.toolName ?? '', data.arguments ?? {});
      } else if (kind === 'tool_result') {
        appendToolResult(id, data.toolResult ?? data.content ?? '');
      } else if (kind === 'done') {
        appendAssistantText(id, data.content ?? '');
        // 推理完成后自动折叠
        const target = items.value.find((it) => it.id === id);
        if (target) {
          target.streaming = false;
          target.reasoningComplete = true;
          collapseReasoning(id);
        }
        currentResponseId.value = null;
        es.close();
        loading.value = false;
      }
      scrollToBottom(true);
    } catch {
      // 忽略解析错误
    }
  });

  es.onerror = () => {
    es.close();
    if (loading.value) {
      currentResponseId.value = null;
      loading.value = false;
    }
  };

  es.addEventListener('close', () => {
    loading.value = false;
    currentEventSource.value = null;
    currentResponseId.value = null;
  });
}

// ── 数据追加辅助函数 ─────────────────────────────────────────────────
function appendAssistantText(id: string, text: string) {
  const target = items.value.find((it) => it.id === id);
  if (!target) return;
  target.content += text;
}

function appendAssistantReasoning(id: string, text: string) {
  const target = items.value.find((it) => it.id === id);
  if (!target) return;
  target.reasoning = (target.reasoning ?? '') + text;
  target.reasoningOpen = true;
}

function appendToolCall(id: string, name: string, args: Record<string, unknown>) {
  const target = items.value.find((it) => it.id === id);
  if (!target) return;
  target.toolCalls = target.toolCalls ?? [];
  target.toolCalls.push({ name, arguments: JSON.stringify(args, null, 2) });
}

function appendToolResult(id: string, text: string) {
  const target = items.value.find((it) => it.id === id);
  if (!target) return;
  target.toolResults = target.toolResults ?? [];
  target.toolResults.push(text);
}

function collapseReasoning(id: string) {
  const target = items.value.find((it) => it.id === id);
  if (target && target.reasoningOpen) {
    target.reasoningOpen = false;
  }
}

// ── 侧边栏操作 ───────────────────────────────────────────────────────
function newSession() {
  const id = randomId();
  sessions.value.unshift({ id, title: '新对话', time: '刚刚' });
  currentSessionId.value = id;
  items.value = [];
  sessionTitle.value = '新对话';
}

function switchSession(id: string) {
  currentSessionId.value = id;
  const s = sessions.value.find((x) => x.id === id);
  sessionTitle.value = s?.title ?? '对话';
  items.value = [];
}

function clearChat() {
  items.value = [];
}

// ── Composer 辅助 ────────────────────────────────────────────────────
function autoResize() {
  nextTick(() => {
    if (inputEl.value) {
      inputEl.value.style.height = 'auto';
      inputEl.value.style.height = `${Math.min(inputEl.value.scrollHeight, 200)}px`;
    }
  });
}

function insertNewline() {
  const el = inputEl.value;
  if (!el) return;
  const start = el.selectionStart;
  const end = el.selectionEnd;
  input.value = input.value.slice(0, start) + '\n' + input.value.slice(end);
  nextTick(() => {
    el.selectionStart = el.selectionEnd = start + 1;
    autoResize();
  });
}

// ── 滚动 ─────────────────────────────────────────────────────────────
function scrollToBottom(smooth = false) {
  nextTick(() => {
    const el = messagesEl.value;
    if (!el) return;
    if (smooth && typeof gsap !== 'undefined') {
      gsap.to(el, {
        duration: 0.28,
        scrollTop: el.scrollHeight,
        ease: 'power1.out',
      });
    } else {
      el.scrollTop = el.scrollHeight;
    }
  });
}

// ── 工具函数 ─────────────────────────────────────────────────────────
function randomId(): string {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}

// ── 生命周期 ─────────────────────────────────────────────────────────
onMounted(() => {
  autoResize();
});
</script>

<style src="@/styles/design-tokens.css"></style>
<style src="@/styles/chat-layout.css"></style>
<style>
/* ── App.vue 私有补丁 ──────────────────────────────────────────────── */

/* 聊天主体 + 侧边栏 网格布局 */
.chat-body {
  display: flex;
  flex: 1;
  overflow: hidden;
}

/* 欢迎页 */
.welcome {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  text-align: center;
  color: var(--fg-dim);
  gap: 10px;
  animation: fadeSlideIn 0.4s var(--ease-out) both;
}

.welcome__icon {
  color: var(--accent);
  opacity: 0.8;
}

.welcome__title {
  font-size: 22px;
  font-weight: var(--font-weight-strong);
  margin: 0;
  color: var(--fg);
}

.welcome__sub {
  font-size: var(--text-md);
  margin: 0;
  color: var(--fg-faint);
}

/* sidebar item 文字截断 */
.sidebar__item-text {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 工具调用区块 */
.tool-calls {
  margin-bottom: 6px;
}

.tool-calls__header-row {
  margin-bottom: 2px;
}

.tool__head--compact {
  padding: 2px 4px;
  min-height: 22px;
  font-size: var(--text-2xs);
}

.tool__meta {
  font-size: var(--text-2xs);
  color: var(--fg-faint);
  margin-left: 4px;
}

.tool-calls__body {
  animation: fadeSlideIn var(--dur-base) var(--ease-out) both;
}

/* 消息进入动画 */
@keyframes msg-enter {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
