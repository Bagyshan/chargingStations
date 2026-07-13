/** Конфигурация слоя данных (Фаза 2). */

/** Источник данных: mock (по умолчанию) или api (реальный шлюз). */
export const USE_MOCK = (import.meta.env.VITE_DATA_SOURCE ?? 'mock') !== 'api';

/**
 * База для запросов. Пусто → относительные пути (в dev проксируются Vite'ом на
 * шлюз, в prod — nginx). Можно задать абсолютный URL шлюза через VITE_API_BASE.
 */
export const API_BASE = import.meta.env.VITE_API_BASE ?? '';

/** Префиксы сервисов на api-gateway (StripPrefix=1). */
export const SERVICE = {
  user: '/user',
  contractorAdmin: '/contractor-admin',
  stationControll: '/station-controll',
  payment: '/payment',
  booking: '/booking',
} as const;
