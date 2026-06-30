import type { WireEvent } from "./types";

/**
 * sseBus 是一个轻量 SSE 事件总线，用于 Java 后端模式。
 *
 * <p>它把 Server-Sent Events 的 `data:` 帧解析为统一的 WireEvent，并支持多订阅者。
 * 在桌面端通过 EventSource 连接 `/api/chat/stream`；在浏览器或失败时降级为空事件。
 */

type Handler = (event: WireEvent) => void;

type Listener = {
  name: string;
  handler: Handler;
};

export class SseBus {
  private readonly source: EventSource | null;
  private readonly listeners: Listener[] = [];
  private ready = false;

  constructor(url: string) {
    this.source = typeof window !== "undefined" ? new EventSource(url) : null;
    if (this.source) {
      this.source.addEventListener("message", (evt) => this.onMessage(evt));
      this.source.addEventListener("error", () => this.onError());
    }
  }

  /**
   * 订阅事件流。
   *
   * @param name   订阅名称，仅用于调试
   * @param handler 事件处理函数
   * @return 取消订阅函数
   */
  subscribe(name: string, handler: Handler): () => void {
    this.listeners.push({ name, handler });
    return () => {
      this.listeners.splice(this.listeners.findIndex(it => it.name === name && it.handler === handler), 1);
    };
  }

  /**
   * 是否就绪。
   */
  isReady(): boolean {
    return this.ready;
  }

  /**
   * 关闭连接。
   */
  close(): void {
    if (this.source) {
      this.source.close();
    }
  }

  private onMessage(evt: MessageEvent): void {
    let data: WireEvent;
    try {
      data = JSON.parse(evt.data) as WireEvent;
    } catch {
      data = { kind: "unknown", text: String(evt.data) };
    }
    for (const listener of this.listeners) {
      try {
        listener.handler(data);
      } catch {
        // ignore listener errors
      }
    }
    if (data.kind === "done" || data.kind === "error" || data.kind === "compact_done") {
      this.ready = true;
    }
  }

  private onError(): void {
    // EventSource 会尝试重连；此处仅做状态记录
    this.ready = false;
  }
}
