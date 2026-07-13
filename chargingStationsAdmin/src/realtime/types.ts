/** Формы сообщений websocket-service (`/ws/station-events`). */

export interface WsStationState {
  stationId?: string;
  online?: boolean;
  serviceStatus?: string;
  version?: number;
  connectors?: { connectorId: number; status: string }[];
}

export interface WsStationEvent {
  eventId?: string;
  eventType:
    | 'STATION_CREATED'
    | 'STATION_UPDATED'
    | 'STATION_DELETED'
    | 'CONNECTOR_STATUS_CHANGED'
    | 'LOCATION_UPDATED'
    | 'VERSION_INCREMENTED'
    | 'METER_VALUE'
    | 'TARIFF_UPDATED';
  stationId?: string;
  stationVersion?: number;
  stationState?: WsStationState;
}

export interface WsMessage {
  type: 'EVENT' | 'PING' | 'PONG' | 'SUBSCRIPTION' | 'UNSUBSCRIPTION' | 'ERROR' | string;
  event?: WsStationEvent;
  stationId?: string;
  message?: string;
  timestamp?: number;
}
