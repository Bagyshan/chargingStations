/** Разбор JWT (payload) на клиенте — только чтение claim'ов, без валидации подписи. */
import type { Role } from '@/types/domain';

type Claims = Record<string, unknown>;

export function decodeJwt(token: string | null | undefined): Claims | null {
  if (!token) return null;
  try {
    const payload = token.split('.')[1];
    const b64 = payload.replace(/-/g, '+').replace(/_/g, '/');
    const json = decodeURIComponent(
      atob(b64)
        .split('')
        .map((c) => '%' + c.charCodeAt(0).toString(16).padStart(2, '0'))
        .join(''),
    );
    return JSON.parse(json) as Claims;
  } catch {
    return null;
  }
}

export function subOf(token: string | null | undefined): string | null {
  const c = decodeJwt(token);
  return (c?.sub as string) ?? null;
}

/** Роли из realm_access.roles Keycloak-токена. */
export function rolesOf(token: string | null | undefined): string[] {
  const c = decodeJwt(token);
  const realm = c?.realm_access as { roles?: string[] } | undefined;
  return (realm?.roles ?? []).map((r) => r.toUpperCase());
}

const PRIORITY: Role[] = ['ADMIN', 'SPECIALIST', 'CONTRACTOR', 'USER'];

/** Наивысшая по привилегиям управляющая роль из списка. */
export function pickRole(roles: string[]): Role {
  for (const r of PRIORITY) if (roles.includes(r)) return r;
  return 'USER';
}
