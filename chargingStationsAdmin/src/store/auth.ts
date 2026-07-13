import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { authProvider } from '@/api/client';
import { AUTH_EXPIRED_EVENT } from '@/api/token-store';
import type { Account, AuthScope } from '@/types/api';
import type { Role } from '@/types/domain';

interface AuthState {
  account: Account | null;
  loading: boolean;
  error: string | null;
  canImpersonate: boolean;
  login: (email: string, password: string) => Promise<void>;
  /** Демо: мгновенно переключить роль без пароля (только в мок-режиме). */
  impersonate: (role: Role) => void;
  logout: () => void;
  scope: () => AuthScope;
}

export const useAuth = create<AuthState>()(
  persist(
    (set, get) => ({
      account: null,
      loading: false,
      error: null,
      canImpersonate: authProvider.canImpersonate,

      async login(email, password) {
        set({ loading: true, error: null });
        try {
          const account = await authProvider.login(email, password);
          set({ account, loading: false });
        } catch (e) {
          set({ loading: false, error: (e as Error).message });
          throw e;
        }
      },

      impersonate(role) {
        const account = authProvider.impersonate?.(role) ?? null;
        if (account) set({ account, error: null });
      },

      logout() {
        authProvider.logout();
        set({ account: null });
      },

      scope() {
        const acc = get().account;
        return { role: acc?.role ?? 'USER', ownerId: acc?.ownerId };
      },
    }),
    {
      name: 'batenergy-admin-auth',
      partialize: (s) => ({ account: s.account }),
    },
  ),
);

// Сессия окончательно истекла (refresh отклонён) — выходим и уводим на вход.
if (typeof window !== 'undefined') {
  window.addEventListener(AUTH_EXPIRED_EVENT, () => {
    useAuth.getState().logout();
    if (!window.location.pathname.startsWith('/login')) window.location.href = '/login';
  });
}
