/**
 * Самовосстанавливающийся WebSocket-клиент к websocket-service.
 * Канал широковещательный: `ws://<host>/websocket/ws/station-events?token=<JWT>`.
 * Сервер шлёт PING (~30с) — отвечаем PONG, иначе через 2 мин соединение рвётся.
 */
import { API_BASE } from '@/api/config';
import type { RealtimeStatus } from '@/store/realtime';
import type { WsMessage, WsStationEvent } from './types';

interface Handlers {
  getToken: () => string | null;
  onStatus: (s: RealtimeStatus) => void;
  onEvent: (e: WsStationEvent) => void;
}

export class WsClient {
  private ws: WebSocket | null = null;
  private closedByUs = false;
  private retry = 0;
  private timer: ReturnType<typeof setTimeout> | null = null;

  constructor(private readonly h: Handlers) {}

  start() {
    this.closedByUs = false;
    this.open();
  }

  stop() {
    this.closedByUs = true;
    if (this.timer) clearTimeout(this.timer);
    this.ws?.close();
    this.ws = null;
    this.h.onStatus('off');
  }

  private wsUrl(token: string): string {
    const base = (API_BASE || window.location.origin).replace(/^http/, 'ws');
    return `${base}/websocket/ws/station-events?token=${encodeURIComponent(token)}`;
  }

  private open() {
    const token = this.h.getToken();
    if (!token) {
      this.scheduleReconnect();
      return;
    }
    this.h.onStatus('connecting');
    let ws: WebSocket;
    try {
      ws = new WebSocket(this.wsUrl(token));
    } catch {
      this.scheduleReconnect();
      return;
    }
    this.ws = ws;

    ws.onopen = () => {
      this.retry = 0;
      this.h.onStatus('live');
    };

    ws.onmessage = (ev) => {
      let msg: WsMessage;
      try {
        msg = JSON.parse(ev.data as string);
      } catch {
        return;
      }
      if (msg.type === 'PING') {
        try {
          ws.send(JSON.stringify({ type: 'PONG' }));
        } catch {
          /* сокет уже закрыт */
        }
        return;
      }
      if (msg.type === 'EVENT' && msg.event) this.h.onEvent(msg.event);
    };

    ws.onerror = () => ws.close();

    ws.onclose = () => {
      this.ws = null;
      if (!this.closedByUs) {
        this.h.onStatus('connecting');
        this.scheduleReconnect();
      }
    };
  }

  private scheduleReconnect() {
    if (this.closedByUs) return;
    const delay = Math.min(1000 * 2 ** this.retry, 15_000);
    this.retry += 1;
    if (this.timer) clearTimeout(this.timer);
    this.timer = setTimeout(() => this.open(), delay);
  }
}
