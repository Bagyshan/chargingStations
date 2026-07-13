/**
 * Хуки данных на TanStack Query. Источник (`api`) — mock или реальный шлюз
 * (см. client.ts). Ключи запросов включают роль/ownerId, поэтому смена роли
 * автоматически перезапрашивает данные.
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/api/client';
import { useAuth } from '@/store/auth';
import type { AnalyticsOptions } from '@/types/api';
import type { ChargeBox, Connector, Role, ServiceStatus } from '@/types/domain';

function useScopeKey() {
  const account = useAuth((s) => s.account);
  return [account?.role ?? 'USER', account?.ownerId ?? '-'] as const;
}

export function useStations() {
  const scope = useAuth((s) => s.scope)();
  const key = useScopeKey();
  return useQuery({ queryKey: ['stations', ...key], queryFn: () => api.getStations(scope) });
}

export function useStation(chargeBoxId: string) {
  const scope = useAuth((s) => s.scope)();
  const key = useScopeKey();
  return useQuery({
    queryKey: ['station', chargeBoxId, ...key],
    queryFn: () => api.getStation(scope, chargeBoxId),
  });
}

export function useConnectors(chargeBoxId?: string) {
  const scope = useAuth((s) => s.scope)();
  const key = useScopeKey();
  return useQuery({
    queryKey: ['connectors', chargeBoxId ?? 'all', ...key],
    queryFn: () => api.getConnectors(scope, chargeBoxId),
  });
}

export function useBookings() {
  const scope = useAuth((s) => s.scope)();
  const key = useScopeKey();
  return useQuery({ queryKey: ['bookings', ...key], queryFn: () => api.getBookings(scope) });
}

export function useTransactions() {
  const scope = useAuth((s) => s.scope)();
  const key = useScopeKey();
  return useQuery({ queryKey: ['transactions', ...key], queryFn: () => api.getTransactions(scope) });
}

export function useAddresses() {
  return useQuery({ queryKey: ['addresses'], queryFn: () => api.getAddresses() });
}

export function useConnectorTypes() {
  return useQuery({ queryKey: ['connector-types'], queryFn: () => api.getConnectorTypes() });
}

export function useOwners() {
  return useQuery({ queryKey: ['owners'], queryFn: () => api.getOwners() });
}

export function useUsers() {
  const scope = useAuth((s) => s.scope)();
  const key = useScopeKey();
  return useQuery({
    queryKey: ['users', ...key],
    queryFn: () => api.getUsers(scope),
    enabled: scope.role === 'ADMIN',
    retry: false,
  });
}

export function useEnergyAnalytics(opts: AnalyticsOptions) {
  const scope = useAuth((s) => s.scope)();
  const key = useScopeKey();
  return useQuery({
    queryKey: ['analytics-energy', opts, ...key],
    queryFn: () => api.getEnergyAnalytics(scope, opts),
  });
}

export function useRevenueAnalytics(opts: AnalyticsOptions) {
  const scope = useAuth((s) => s.scope)();
  const key = useScopeKey();
  return useQuery({
    queryKey: ['analytics-revenue', opts, ...key],
    queryFn: () => api.getRevenueAnalytics(scope, opts),
  });
}

export function useBookingAnalytics(opts: AnalyticsOptions) {
  const scope = useAuth((s) => s.scope)();
  const key = useScopeKey();
  return useQuery({
    queryKey: ['analytics-bookings', opts, ...key],
    queryFn: () => api.getBookingAnalytics(scope, opts),
  });
}

/* ------------------------------ Мутации ------------------------------ */

export function useCreateStation() {
  const scope = useAuth((s) => s.scope)();
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: Partial<ChargeBox>) => api.createStation(scope, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['stations'] }),
  });
}

export function useUpdateStation() {
  const scope = useAuth((s) => s.scope)();
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: number; data: Partial<ChargeBox> }) =>
      api.updateStation(scope, id, data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['stations'] });
      qc.invalidateQueries({ queryKey: ['station'] });
    },
  });
}

export function useCreateConnector() {
  const scope = useAuth((s) => s.scope)();
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: Partial<Connector>) => api.createConnector(scope, data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['connectors'] });
      qc.invalidateQueries({ queryKey: ['stations'] });
    },
  });
}

export function useUpdateConnector() {
  const scope = useAuth((s) => s.scope)();
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: number; data: Partial<Connector> }) =>
      api.updateConnector(scope, id, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['connectors'] }),
  });
}

export function useDeleteConnector() {
  const scope = useAuth((s) => s.scope)();
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => api.deleteConnector(scope, id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['connectors'] });
      qc.invalidateQueries({ queryKey: ['stations'] });
    },
  });
}

export function useSetServiceStatus() {
  const scope = useAuth((s) => s.scope)();
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ chargeBoxId, status }: { chargeBoxId: string; status: ServiceStatus }) =>
      api.setServiceStatus(scope, chargeBoxId, status),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['stations'] });
      qc.invalidateQueries({ queryKey: ['station'] });
    },
  });
}

export function useDeleteStation() {
  const scope = useAuth((s) => s.scope)();
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => api.deleteStation(scope, id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['stations'] }),
  });
}

export function useChangeUserRole() {
  const scope = useAuth((s) => s.scope)();
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, role }: { id: number; role: Role }) => api.changeUserRole(scope, id, role),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['users'] }),
  });
}

export function useSetUserActive() {
  const scope = useAuth((s) => s.scope)();
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, active }: { id: number; active: boolean }) =>
      api.setUserActive(scope, id, active),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['users'] }),
  });
}
