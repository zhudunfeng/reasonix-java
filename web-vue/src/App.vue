<template>
  <div class="chat">
    <header class="chat-header">
      <h1>Reasonix Chat</h1>
      <span class="badge">Web 版</span>
    </header>

    <main class="chat-main">
      <div class="messages" ref="messagesEl">
        <div v-for="item in items" :key="item.id" :class="['message', item.role]">
          <div class="bubble">
            <div class="role">{{ item.role }}</div>

            <template v-if="item.role === 'assistant'">
              <!-- 思考过程（可折叠） -->
              <div v-if="item.reasoning && item.reasoningOpen" class="content reasoning">
                <div class="reasoning-header" @click="toggleReasoning(item)">
                  <span>思考过程</span>
                  <span class="caret">▲</span>
                </div>
                <div class="reasoning-body">{{ item.reasoning }}</div>
              </div>
              <div v-else-if="item.reasoning && !item.reasoningOpen"
                   class="content reasoning collapsed"
                   @click="toggleReasoning(item)">
                <div class="reasoning-header">
                  <span>思考过程</span>
                  <span class="caret">▼</span>
                </div>
              </div>

              <!-- 工具调用 -->
              <div v-if="item.toolCalls && item.toolCalls.length" class="content tool-calls">
                <div class="tool-call" v-for="tool in item.toolCalls" :key="tool.name">
                  <div class="tool-name">{{ tool.name }}</div>
                  <pre class="tool-args">{{ tool.arguments }}</pre>
                </div>
              </div>
              <!-- 工具执行结果 -->
              <div v-if="item.toolResults && item.toolResults.length" class="content tool-results">
                <div class="tool-result" v-for="(result, idx) in item.toolResults" :key="idx">
                  {{ result }}
                </div>
              </div>
            </template>

            <!-- 主回复内容 -->
            <div v-if="item.content" class="content">{{ item.content }}</div>
          </div>
        </div>

        <!-- 加载中占位 -->
        <div v-if="loading" class="message assistant">
          <div class="bubble">
            <div class="role">assistant</div>
            <div class="content">正在思考...</div>
          </div>
        </div>
      </div>
    </main>

    <footer class="chat-footer">
      <form class="composer" @submit.prevent="send">
        <input
          v-model="input"
          placeholder="输入消息..."
          :disabled="loading"
          @keydown.enter.prevent="send"
        />
        <button type="submit" :disabled="loading || !input.trim()">发送</button>
      </form>
      <div class="hint">
        打开 <code>http://localhost:5173</code>，后端请先启动 <code>mvn spring-boot:run</code>
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue';

const API_BASE = '';
/** 后端 SSE 端点（GET，通过 EventSource 建立连接） */
const SSE_URL = `/api/chat/stream`;

type Role = 'user' | 'assistant';
type Message = {
  id: string;
  role: Role;
  content: string;
  /** 思考过程文本 */
  reasoning?: string;
  /** 思考过程是否展开 */
  reasoningOpen?: boolean;
  /** 工具调用列表 */
  toolCalls?: { name: string; arguments: string }[];
  /** 工具执行结果列表 */
  toolResults?: string[];
};

const input = ref('');
const loading = ref(false);
const items = ref<Message[]>([]);
const messagesEl = ref<HTMLDivElement | null>(null);
/** 当前活跃的 EventSource 连接，用于快速关闭和竞态控制 */
const currentEventSource = ref<EventSource | null>(null);

/**
 * 发送消息。
 *
 * 流程：
 *  1. 将用户输入追加到 items
 *  2. 建立 EventSource GET /api/chat/stream?question=xxx 连接
 *  3. 逐事件接收 THINK / CHUNK / TOOL_CALL / TOOL_RESULT / DONE / ERROR
 *  4. 实时更新最后一条 assistant 消息
 */
function send() {
  const text = input.value.trim();
  if (!text || loading.value) return;

  const sessionId = sessionIdValue();
  items.value.push({ id: randomId(), role: 'user', content: text });
  input.value = '';
  loading.value = true;
  scrollToBottom();

  // 关闭上一次未完成的 EventSource（防止快速连发时的竞态）
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

      const id = ensureAssistantBubble();

      if (kind === 'error') {
        appendAssistantText(id, `\n[错误] ${data.errorMessage ?? '未知错误'}`);
      } else if (kind === 'start') {
        // 仅占位，不渲染内容
      } else if (kind === 'think') {
        appendAssistantReasoning(id, data.content ?? '');
      } else if (kind === 'chunk') {
        appendAssistantText(id, data.content ?? '');
        collapseLastReasoning(id);
      } else if (kind === 'tool_call') {
        appendAssistantToolCall(id, data.toolName ?? '', data.arguments ?? {});
      } else if (kind === 'tool_result') {
        appendAssistantToolResult(id, data.toolResult ?? data.content ?? '');
      } else if (kind === 'done') {
        appendAssistantText(id, data.content ?? '');
        collapseLastReasoning(id);
        es.close();
        loading.value = false;
      }
      scrollToBottom();
    } catch {
      // 忽略解析错误
    }
  });

  es.onerror = () => {
    es.close();
    if (loading.value) {
      // 连接错误但尚未完成时，不做额外操作（后端 ERROR 事件会处理错误内容）
    }
  };

  // 兜底：若后端没有返回 DONE 而连接关闭，取消 loading
  es.addEventListener('close', () => {
    loading.value = false;
    currentEventSource.value = null;
  });
}

function sessionIdValue(): string {
  let sid = sessionStorage.getItem('reasonix-session-id');
  if (!sid) {
    sid = `web-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
    sessionStorage.setItem('reasonix-session-id', sid);
  }
  return sid;
}

function randomId(): string {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}

/**
 * 确保 items 最后一条是 assistant 消息，返回其 id。
 * 若是连续 assistant 消息则复用，否则追加新消息。
 */
function ensureAssistantBubble(): string {
  const last = items.value[items.value.length - 1];
  if (last && last.role === 'assistant') return last.id;
  const id = randomId();
  items.value.push({ id, role: 'assistant', content: '', reasoningOpen: false });
  return id;
}

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

function appendAssistantToolCall(id: string, name: string, args: Record<string, unknown>) {
  const target = items.value.find((it) => it.id === id);
  if (!target) return;
  target.toolCalls = target.toolCalls ?? [];
  target.toolCalls.push({ name, arguments: JSON.stringify(args, null, 2) });
}

function appendAssistantToolResult(id: string, text: string) {
  const target = items.value.find((it) => it.id === id);
  if (!target) return;
  target.toolResults = target.toolResults ?? [];
  target.toolResults.push(text);
}

function collapseLastReasoning(id: string) {
  const target = items.value.find((it) => it.id === id);
  if (target && target.reasoningOpen) {
    target.reasoningOpen = false;
  }
}

function toggleReasoning(item: Message) {
  item.reasoningOpen = !item.reasoningOpen;
}

function scrollToBottom() {
  return nextTick(() => {
    if (messagesEl.value) messagesEl.value.scrollTop = messagesEl.value.scrollHeight;
  });
}

onMounted(() => {
  // 预留初始化逻辑
});
</script>

<style>
:root {
  color-scheme: dark;
  bg: #1a1a2e;
  panel: #22223a;
  text: #e6e6f0;
  accent: #7aa7ff;
}

* {
  box-sizing: border-box;
}

html, body, #app {
  height: 100%;
}

body {
  margin: 0;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, "Noto Sans", "PingFang SC", "Microsoft YaHei", sans-serif;
  background: #1a1a2e;
  color: #e6e6f0;
}

.chat {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.chat-header {
  border-bottom: 1px solid #2b2b45;
  padding: 16px 20px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.chat-header h1 {
  font-size: 18px;
  margin: 0;
}

.badge {
  font-size: 12px;
  background: #2f356b;
  color: #cdd7ff;
  padding: 2px 8px;
  border-radius: 999px;
}

.chat-main {
  flex: 1;
  overflow: auto;
  padding: 20px;
}

.messages {
  display: flex;
  flex-direction: column;
  gap: 14px;
  max-width: 860px;
  margin: 0 auto;
  width: 100%;
}

.message {
  display: flex;
}

.message.user {
  justify-content: flex-end;
}

.message.assistant {
  justify-content: flex-start;
}

.bubble {
  max-width: 80%;
  border-radius: 16px;
  padding: 10px 14px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
}

.message.user .bubble {
  background: #3c4ee4;
  color: #fff;
  border-bottom-right-radius: 4px;
}

.message.assistant .bubble {
  background: #2a2a40;
  color: #e6e6f0;
  border: 1px solid #353558;
  border-bottom-left-radius: 4px;
}

.role {
  font-size: 12px;
  opacity: 0.75;
  margin-bottom: 4px;
}

.chat-footer {
  border-top: 1px solid #2b2b45;
  padding: 14px 20px;
  background: #1f1f36;
}

.composer {
  max-width: 860px;
  margin: 0 auto;
  display: flex;
  gap: 10px;
}

.composer input {
  flex: 1;
  background: #262640;
  border: 1px solid #34345a;
  color: #e6e6f0;
  padding: 12px 14px;
  border-radius: 12px;
  outline: none;
  font-size: 14px;
}

.composer input:focus {
  border-color: #7aa7ff;
}

.composer button {
  background: #3c4ee4;
  color: #fff;
  border: none;
  padding: 0 18px;
  border-radius: 12px;
  cursor: pointer;
  font-weight: 600;
}

.composer button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.hint {
  max-width: 860px;
  margin: 8px auto 0;
  color: #9aa0c0;
  font-size: 12px;
}

.hint code {
  background: #262640;
  border: 1px solid #34345a;
  padding: 1px 6px;
  border-radius: 6px;
  color: #cdd7ff;
}

.reasoning {
  margin-bottom: 8px;
}

.reasoning-header {
  cursor: pointer;
  font-size: 12px;
  opacity: 0.8;
  display: flex;
  align-items: center;
  gap: 6px;
}

.caret {
  font-size: 10px;
}

.reasoning-body {
  margin-top: 6px;
  opacity: 0.85;
  white-space: pre-wrap;
}

.collapsed .reasoning-body {
  display: none;
}

.tool-calls {
  margin-bottom: 8px;
}

.tool-call {
  background: #1f2040;
  border: 1px solid #34345a;
  border-radius: 10px;
  padding: 8px 10px;
  margin-bottom: 6px;
}

.tool-name {
  font-size: 12px;
  opacity: 0.85;
  margin-bottom: 4px;
}

.tool-args {
  margin: 0;
  font-size: 12px;
  white-space: pre-wrap;
}

.tool-results {
  margin-bottom: 8px;
}

.tool-result {
  background: #1b1b32;
  border: 1px solid #2f356b;
  border-radius: 10px;
  padding: 8px 10px;
  margin-bottom: 6px;
  white-space: pre-wrap;
  font-size: 13px;
}
</style>
