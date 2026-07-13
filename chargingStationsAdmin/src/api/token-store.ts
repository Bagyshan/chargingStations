/**
 * Хранилище токенов сессии (access/refresh) в localStorage. Переживает
 * перезагрузку вкладки. Используется fetch-клиентом ([http.ts]) для заголовка
 * Authorization и обновления access-токена по refresh при 401.
 */
import { subOf } from './jwt';

const K_ACCESS = 'batenergy.admin.access';
const K_REFRESH = 'batenergy.admin.refresh';
const K_EXPIRES = 'batenergy.admin.expiresAt';

/** Событие «сессия истекла» — слушается стором авторизации для выхода. */
export const AUTH_EXPIRED_EVENT = 'batenergy:auth-expired';

function read(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

let accessToken = read(K_ACCESS);
let refreshToken = read(K_REFRESH);
let expiresAt = read(K_EXPIRES) ? Number(read(K_EXPIRES)) : null;

export const tokenStore = {
  get access() {
    return accessToken;
  },
  get refresh() {
    return refreshToken;
  },
  get keycloakId() {
    return subOf(accessToken);
  },
  hasSession() {
    return !!accessToken;
  },
  isExpired() {
    return expiresAt != null && Date.now() > expiresAt - 10_000;
  },
  set(access: string, refresh: string, expiresInSeconds?: number | null) {
    accessToken = access;
    refreshToken = refresh;
    expiresAt = expiresInSeconds ? Date.now() + expiresInSeconds * 1000 : null;
    try {
      localStorage.setItem(K_ACCESS, access);
      localStorage.setItem(K_REFRESH, refresh);
      if (expiresAt) localStorage.setItem(K_EXPIRES, String(expiresAt));
      else localStorage.removeItem(K_EXPIRES);
    } catch {
      /* приватный режим — держим только в памяти */
    }
  },
  clear() {
    accessToken = null;
    refreshToken = null;
    expiresAt = null;
    try {
      localStorage.removeItem(K_ACCESS);
      localStorage.removeItem(K_REFRESH);
      localStorage.removeItem(K_EXPIRES);
    } catch {
      /* no-op */
    }
  },
  /** Сессия окончательно мертва (refresh отклонён) — чистим и уведомляем UI. */
  expire() {
    this.clear();
    if (typeof window !== 'undefined') window.dispatchEvent(new Event(AUTH_EXPIRED_EVENT));
  },
};
