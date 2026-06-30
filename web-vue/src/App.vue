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

            <div v-if="item.reasoning" class="content reasoning">
              <div class="reasoning-header" @click="toggleReasoning(item)">
                <span>思考过程</span>
                <span class="caret">{{ item.reasoningOpen ? '▲' : '▼' }}</span>
              </div>
              <div v-if="item.reasoningOpen" class="reasoning-body">{{ item.reasoning }}</div>
            </div>

            <div v-if="item.content" class="content">{{ item.content }}</div>
          </div>
        </div>

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
const SSE_URL = `/api/chat/stream`;

type Role = 'user' | 'assistant';
type Message = { id: string; role: Role; content: string; reasoning?: string; reasoningOpen?: boolean };

const input = ref('');
const loading = ref(false);
const items = ref<Message[]>([]);
const messagesEl = ref<HTMLDivElement | null>(null);

async function send() {
  const text = input.value.trim();
  if (!text || loading.value) return;

  const sessionId = sessionIdValue();
  items.value.push({ id: randomId(), role: 'user', content: text });
  input.value = '';
  loading.value = true;
  await scrollToBottom();

  try {
    const res = await fetch(`${API_BASE}/api/chat/stream`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ query: text, sessionId, method: 'Submit' }),
    });
    if (!res.ok) {
      const text = await res.text();
      throw new Error(`HTTP ${res.status}: ${text}`);
    }

    const reader = res.body?.getReader();
    if (!reader) {
      throw new Error('响应流不可读');
    }

    const decoder = new TextDecoder();
    let buffer = '';
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split(/\r?\n/);
      buffer = lines.pop() ?? '';
      for (const line of lines) {
        const trimmed = line.trim();
        if (!trimmed || !trimmed.startsWith('data:')) continue;
        const payload = trimmed.slice(5).trim();
        if (!payload) continue;
        try {
          const data = JSON.parse(payload) as { type?: string; kind?: string; content?: string; errorMessage?: string; reasoning?: string };
          if (!data) return;
          const kind = String(data.kind ?? data.type ?? "").toLowerCase();
          if (!kind || kind === "unknown") return;
          const id = ensureAssistantBubble();
          if (kind === 'error') {
            appendAssistantText(id, `\n[错误] ${data.errorMessage ?? '未知错误'}`);
          } else if (kind === 'start') {
            appendAssistantText(id, '');
          } else if (kind === 'done' || kind === 'token') {
            appendAssistantText(id, data.content ?? '');
            collapseLastReasoning(id);
          } else if (kind === 'reasoning' || kind === 'thinking') {
            appendAssistantReasoning(id, data.reasoning ?? data.content ?? '');
          }
          await scrollToBottom();
        } catch {
          // ignore parse errors
        }
      }
    }
  } catch (e) {
    items.value.push({ id: randomId(), role: 'assistant', content: `请求失败：${(e as Error).message}` });
  } finally {
    loading.value = false;
    await scrollToBottom();
  }
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
</style>
