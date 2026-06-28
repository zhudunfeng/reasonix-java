const API_BASE = '/api';

export async function chatAgentAsk(question: string) {
  const res = await fetch(`${API_BASE}/chat/agent/ask`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ question }),
  });
  return res.json();
}

export async function chatStream(question: string, onEvent: (event: any) => void) {
  const eventSource = new EventSource(`${API_BASE}/chat/stream?question=${encodeURIComponent(question)}`);
  eventSource.onmessage = (event) => {
    const data = JSON.parse(event.data);
    onEvent(data);
  };
  eventSource.onerror = () => eventSource.close();
  return eventSource;
}
