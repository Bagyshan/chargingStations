/**
 * Точка выбора источника данных: mock (по умолчанию) или реальный api-gateway.
 * Управляется VITE_DATA_SOURCE. Весь UI ходит только сюда — за `api` и `authProvider`.
 */
import { USE_MOCK } from './config';
import { backendApi, backendAuth } from './backend-api';
import { DEMO_ACCOUNTS, mockApi, type DemoAccount } from '@/mock/api';
import type { Account, AuthProvider, DataApi } from '@/types/api';

export { USE_MOCK };

export const api: DataApi = USE_MOCK ? mockApi : backendApi;

function toAccount(a: DemoAccount): Account {
  return {
    role: a.role,
    ownerId: a.ownerId,
    email: a.email,
    firstName: a.user.firstName,
    lastName: a.user.lastName,
    id: a.user.id,
    keycloakId: a.user.keycloakId,
  };
}

const mockAuth: AuthProvider = {
  async login(email, password) {
    return toAccount(await mockApi.login(email, password));
  },
  logout() {},
  restore() {
    return null;
  },
  canImpersonate: true,
  impersonate(role) {
    const a = DEMO_ACCOUNTS.find((x) => x.role === role);
    return a ? toAccount(a) : null;
  },
};

export const authProvider: AuthProvider = USE_MOCK ? mockAuth : backendAuth;
