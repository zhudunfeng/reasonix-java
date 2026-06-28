import ReactMarkdown from 'react-markdown';
import { useChatStore } from '../stores/chatStore';

export default function ChatPage() {
  const { messages, input, setInput, send, loading } = useChatStore();

  return (
    <div className="mx-auto flex h-screen max-w-3xl flex-col px-4 py-6">
      <header className="mb-4 flex items-center justify-between">
        <h1 className="text-xl font-semibold">Reansonix</h1>
        <span className="text-xs text-slate-400">Reasonix Java 复刻</span>
      </header>

      <div className="flex-1 space-y-4 overflow-y-auto rounded-xl border border-slate-800 bg-slate-900/60 p-4">
        {messages.length === 0 && (
          <div className="text-sm text-slate-400">输入问题开始对话...</div>
        )}
        {messages.map((message) => (
          <div
            key={message.id}
            className={`rounded-lg p-3 ${
              message.role === 'user'
                ? 'ml-auto max-w-[80%] bg-indigo-600/20'
                : 'mr-auto max-w-[80%] bg-slate-800'
            }`}
          >
            <div className="mb-1 text-xs text-slate-400">{message.role}</div>
            <div className="text-sm leading-relaxed">
              <ReactMarkdown>{message.content}</ReactMarkdown>
            </div>
          </div>
        ))}
      </div>

      <div className="mt-4 flex items-center gap-2">
        <input
          className="flex-1 rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-sm outline-none focus:border-indigo-500"
          value={input}
          placeholder="输入消息..."
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
              e.preventDefault();
              send();
            }
          }}
        />
        <button
          className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium disabled:opacity-50"
          onClick={send}
          disabled={loading}
        >
          {loading ? '发送中...' : '发送'}
        </button>
      </div>
    </div>
  );
}
