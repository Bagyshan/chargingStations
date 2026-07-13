/**
 * Подключает realtime-канал (только в api-режиме) и применяет события к кэшу
 * TanStack Query: онлайн/сервис-статус станций и статусы коннекторов обновляются
 * мгновенно, без перезапроса. Создание/удаление станции — инвалидация списка.
 */
import { useEffect } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { USE_MOCK } from '@/api/client';
import { tokenStore } from '@/api/token-store';
import { useAuth } from '@/store/auth';
import { useRealtimeStore } from '@/store/realtime';
import { WsClient } from './ws-client';
import type { WsStationEvent } from './types';
import type { ChargeBox, Connector, ConnectorStatus, ServiceStatus } from '@/types/domain';
import type { QueryClient } from '@tanstack/react-query';

function applyEvent(qc: QueryClient, e: WsStationEvent) {
  const sid = e.stationId ?? e.stationState?.stationId;
  const st = e.stationState;

  if (sid && st) {
    qc.setQueriesData<ChargeBox[]>({ queryKey: ['stations'] }, (old) =>
      old?.map((s) =>
        s.chargeBoxId === sid
          ? {
              ...s,
              online: st.online ?? s.online,
              serviceStatus: (st.serviceStatus as ServiceStatus) ?? s.serviceStatus,
            }
          : s,
      ),
    );
    if (st.connectors?.length) {
      qc.setQueriesData<Connector[]>({ queryKey: ['connectors'] }, (old) =>
        old?.map((c) => {
          if (c.chargeBoxId !== sid) return c;
          const match = st.connectors!.find((x) => x.connectorId === c.connectorId);
          return match ? { ...c, status: match.status as ConnectorStatus } : c;
        }),
      );
    }
  }

  if (e.eventType === 'STATION_CREATED' || e.eventType === 'STATION_DELETED') {
    qc.invalidateQueries({ queryKey: ['stations'] });
    qc.invalidateQueries({ queryKey: ['connectors'] });
  }
}

export function useRealtime() {
  const qc = useQueryClient();
  const account = useAuth((s) => s.account);
  const setStatus = useRealtimeStore((s) => s.setStatus);
  const markEvent = useRealtimeStore((s) => s.markEvent);

  useEffect(() => {
    if (USE_MOCK || !account) return;
    const client = new WsClient({
      getToken: () => tokenStore.access,
      onStatus: setStatus,
      onEvent: (e) => {
        markEvent();
        applyEvent(qc, e);
      },
    });
    client.start();
    return () => client.stop();
  }, [account, qc, setStatus, markEvent]);
}
