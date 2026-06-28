import { create } from 'zustand';

export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant' | 'system';
  content: string;
}

export interface ChatState {
  messages: ChatMessage[];
  input: string;
  loading: boolean;
  setInput: (input: string) => void;
  send: () => Promise<void>;
}

export const useChatStore = create<ChatState>((set, get) => ({
  messages: [],
  input: '',
  loading: false,
  setInput: (input) => set({ input }),
  send: async () => {
    const input = get().input.trim();
    if (!input) return;
    const userMessage = { id: crypto.randomUUID(), role: 'user' as const, content: input };
    set({ messages: [...get().messages, userMessage], input: '', loading: true });
    try {
      const res = await fetch('/api/chat/agent/ask', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ question: input }),
      });
      const data = (await res.json()) as { content?: string; error?: string };
      const assistantMessage = {
        id: crypto.randomUUID(),
        role: 'assistant' as const,
        content: data.content || data.error || '(empty)',
      };
      set({ messages: [...get().messages, assistantMessage] });
    } catch (e) {
      set({
        messages: [
          ...get().messages,
          { id: crypto.randomUUID(), role: 'assistant', content: '请求失败: ' + String(e) },
        ],
      });
    } finally {
      set({ loading: false });
    }
  },
}));
