/**
 * Доступ к журналу аудита (audit-log-service через api-gateway `/audit`).
 * Эндпоинт защищён ролями ADMIN/SPECIALIST на бэкенде. Работает только в api-режиме
 * (в мок-режиме реального шлюза нет).
 */
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { SERVICE } from './config';
import { request } from './http';

export interface AuditEvent {
  eventId: string;
  timestamp?: string;
  eventType?: string;
  action?: string;
  userId?: string;
  source?: string;
  severity?: string;
  entityId?: string;
  correlationId?: string;
  ip?: string;
  userAgent?: string;
  message?: string;
  payload?: Record<string, unknown>;
}

export interface AuditPage {
  content: AuditEvent[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface AuditQuery {
  eventType?: string;
  action?: string;
  userId?: string;
  entityId?: string;
  source?: string;
  severity?: string;
  q?: string;
  /** ISO-8601 instant (напр. 2026-07-17T00:00:00.000Z). */
  from?: string;
  to?: string;
  page: number;
  size: number;
}

function toParams(query: AuditQuery): string {
  const p = new URLSearchParams();
  const add = (k: string, v?: string) => {
    if (v && v.trim()) p.set(k, v.trim());
  };
  add('eventType', query.eventType);
  add('action', query.action);
  add('userId', query.userId);
  add('entityId', query.entityId);
  add('source', query.source);
  add('severity', query.severity);
  add('q', query.q);
  add('from', query.from);
  add('to', query.to);
  p.set('page', String(query.page));
  p.set('size', String(query.size));
  return p.toString();
}

export function fetchAuditEvents(query: AuditQuery): Promise<AuditPage> {
  return request<AuditPage>(`${SERVICE.audit}/api/audit/events?${toParams(query)}`);
}

export function useAuditEvents(query: AuditQuery) {
  return useQuery({
    queryKey: ['audit', query],
    queryFn: () => fetchAuditEvents(query),
    placeholderData: keepPreviousData,
  });
}
