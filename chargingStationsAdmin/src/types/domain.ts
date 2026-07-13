/**
 * Доменные модели админ-панели.
 *
 * Поля повторяют DTO бэкенда (contractor-admin-service, user-service,
 * station-controll-service, payment-service) — при переходе на реальный API
 * в Фазе 2 мок-слой меняется на fetch без правки этих типов.
 */

/** Роли Keycloak-realm (см. UserRole enum на бэкенде). */
export type Role = 'ADMIN' | 'SPECIALIST' | 'CONTRACTOR' | 'USER';

export const ROLE_LABELS: Record<Role, string> = {
  ADMIN: 'Администратор',
  SPECIALIST: 'Специалист',
  CONTRACTOR: 'Контрагент',
  USER: 'Пользователь',
};

/** Статус коннектора (OCPP StatusNotification). */
export type ConnectorStatus =
  | 'Available'
  | 'Charging'
  | 'Preparing'
  | 'Finishing'
  | 'Reserved'
  | 'Unavailable'
  | 'Faulted';

/** Эксплуатационный статус станции (оператор). */
export type ServiceStatus = 'IN_SERVICE' | 'OUT_OF_SERVICE' | 'MAINTENANCE';

export type TransactionStatus = 'ACTIVE' | 'COMPLETED' | 'CANCELLED' | 'REJECTED';

export type BookingStatus =
  | 'START_RESERVATION'
  | 'STOP_RESERVATION'
  | 'ACTIVE'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'REJECTED';

/** Charge box = зарядная станция (contractor-admin ChargeBoxResponse). */
export interface ChargeBox {
  id: number;
  chargeBoxId: string;
  ocppProtocol: string | null;
  chargePointVendor: string | null;
  chargePointModel: string | null;
  chargePointSerialNumber: string | null;
  chargeBoxSerialNumber: string | null;
  firmwareVersion: string | null;
  iccid: string | null;
  imsi: string | null;
  meterType: string | null;
  meterSerialNumber: string | null;
  ocppTag: string | null;
  createdAt: string;
  ownerId: string | null;
  power: string | null;
  kwCost: number | null;
  bookingMinuteCost: number | null;
  addressId: number | null;
  // Обогащение для UI (join на клиенте):
  addressName?: string;
  ownerName?: string;
  online?: boolean;
  serviceStatus?: ServiceStatus;
  connectorCount?: number;
  latitude?: number;
  longitude?: number;
}

/** Коннектор (contractor-admin ConnectorResponse). */
export interface Connector {
  id: number;
  chargeBoxId: string;
  connectorId: number;
  info: string | null;
  createdAt: string;
  vendorId: string | null;
  status: ConnectorStatus;
  connectorTypeId: number | null;
  // Обогащение:
  connectorTypeName?: string;
  connectorTypeCode?: string;
  power?: string | null;
}

/** Бронирование (contractor-admin BookingResponse). */
export interface Booking {
  id: number;
  bookingId: string;
  userId: string;
  stationId: string;
  connectorId: number;
  pricePerMinute: number;
  totalSum: number;
  totalMinutes: number;
  startedAt: string;
  endedAt: string | null;
  status: BookingStatus;
  createdAt: string;
  userName?: string;
  addressName?: string;
}

/** Транзакция зарядки (contractor-admin TransactionResponse). */
export interface Transaction {
  id: number;
  transactionId: number;
  chargeBoxId: string;
  connectorId: number;
  startTimestamp: string;
  startValue: number;
  stopTimestamp: string | null;
  stopValue: number | null;
  transactionValue: number;
  status: TransactionStatus;
  reason: string | null;
  userId: string;
  createdAt: string;
  updatedAt: string;
  userName?: string;
  addressName?: string;
  totalSum?: number;
}

/** Пользователь (user-service / external UserDto). */
export interface User {
  id: number;
  keycloakId?: string;
  email: string;
  phone: string | null;
  firstName: string | null;
  lastName: string | null;
  role: Role;
  emailVerified: boolean;
  phoneVerified: boolean;
  active: boolean;
  createdAt: string;
  lastLoginAt: string | null;
  balance?: number;
}

/** Адрес станции (external AddressDto). */
export interface Address {
  id: number;
  addressName: string;
  chargeBoxCount: number;
}

/** Тип коннектора (external ConnectorTypeDto / station-controll). */
export interface ConnectorType {
  id: number;
  connectorTypeName: string;
  code: string;
  connectorsCount: number;
  maxPowerKw?: number;
}

/** Почасовой тариф станции (HourTariffRequest). */
export interface HourTariff {
  hour: number; // 0..23
  kwCost: number;
  bookingMinuteCost: number;
}

/* ----------------------------- Аналитика ----------------------------- */

export type Granularity = 'HOUR' | 'DAY' | 'WEEK' | 'MONTH' | 'YEAR';
export type GroupBy = 'TOTAL' | 'STATION' | 'OWNER';

export interface EnergyDataPoint {
  periodStart: string;
  energyKwh: number;
  sessions: number;
  avgDurationMinutes: number;
  revenue: number;
}

export interface EnergySeries {
  groupKey: string;
  label: string;
  points: EnergyDataPoint[];
}

export interface EnergyResponse {
  from: string;
  to: string;
  granularity: Granularity;
  groupBy: GroupBy;
  summary: {
    totalEnergyKwh: number;
    totalSessions: number;
    avgEnergyPerSessionKwh: number;
    avgSessionDurationMinutes: number;
    totalRevenue: number;
    uniqueStations: number;
    uniqueUsers: number;
  };
  series: EnergySeries[];
}

export interface RevenueDataPoint {
  periodStart: string;
  totalRevenue: number;
  chargingRevenue: number;
  bookingRevenue: number;
  chargingSessions: number;
  bookingCount: number;
}

export interface RevenueResponse {
  from: string;
  to: string;
  granularity: Granularity;
  groupBy: GroupBy;
  summary: {
    totalRevenue: number;
    chargingRevenue: number;
    bookingRevenue: number;
    chargingSessions: number;
    bookingCount: number;
  };
  series: { groupKey: string; label: string; points: RevenueDataPoint[] }[];
}

export interface BookingDataPoint {
  periodStart: string;
  bookings: number;
  totalMinutes: number;
  avgDurationMinutes: number;
  revenue: number;
}

export interface BookingAnalyticsResponse {
  from: string;
  to: string;
  granularity: Granularity;
  groupBy: GroupBy;
  summary: {
    totalBookings: number;
    totalMinutes: number;
    avgDurationMinutes: number;
    totalRevenue: number;
  };
  series: { groupKey: string; label: string; points: BookingDataPoint[] }[];
}
