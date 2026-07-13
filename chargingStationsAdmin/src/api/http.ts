/**
 * Fetch-клиент к api-gateway. Подставляет Bearer из [tokenStore], при 401 один
 * раз пытается обновить токен (refresh) и повторяет запрос. Ответы user-service
 * завёрнуты в ApiResponse<T> — разворачиваются флагом `unwrap`.
 */
import { API_BASE, SERVICE } from './config';
import { tokenStore } from './token-store';

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  auth?: boolean;
  unwrap?: boolean;
}

let refreshing: Promise<boolean> | null = null;

async function doRefresh(): Promise<boolean> {
  const token = tokenStore.refresh;
  if (!token) return false;
  try {
    const res = await fetch(`${API_BASE}${SERVICE.user}/api/v1/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify({ refreshToken: token }),
    });
    if (res.ok) {
      const data = (await res.json())?.data;
      if (data?.accessToken) {
        tokenStore.set(data.accessToken, data.refreshToken ?? token, data.expiresIn ?? null);
        return true;
      }
    }
    // Токен окончательно отклонён — гасим сессию (сеть/5xx токены не трогают).
    if (res.status === 400 || res.status === 401 || res.status === 403) tokenStore.expire();
  } catch {
    /* сеть — не трогаем токены, повторим позже */
  }
  return false;
}

function ensureRefresh(): Promise<boolean> {
  if (!refreshing) refreshing = doRefresh().finally(() => (refreshing = null));
  return refreshing;
}

export async function request<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, auth = true, unwrap = false } = opts;
  return exec<T>(path, method, body, auth, unwrap, false);
}

async function exec<T>(
  path: string,
  method: string,
  body: unknown,
  auth: boolean,
  unwrap: boolean,
  retried: boolean,
): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (auth && tokenStore.access) headers['Authorization'] = `Bearer ${tokenStore.access}`;

  const res = await fetch(`${API_BASE}${path}`, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  if (res.status === 401 && auth && !retried) {
    const ok = await ensureRefresh();
    if (ok) return exec<T>(path, method, body, auth, unwrap, true);
  }

  if (!res.ok) {
    let message = res.statusText;
    try {
      const j = await res.json();
      message = j?.message ?? message;
    } catch {
      /* тело без json */
    }
    throw new ApiError(res.status, message);
  }

  if (res.status === 204) return undefined as T;
  const json = await res.json();
  return (unwrap ? json?.data : json) as T;
}
