/**
 * Контракты слоя данных. Один интерфейс [DataApi] реализуют и мок-слой, и
 * реальный клиент к api-gateway — переключение через VITE_DATA_SOURCE.
 */
import type {
  Address,
  Booking,
  BookingAnalyticsResponse,
  ChargeBox,
  Connector,
  ConnectorType,
  EnergyResponse,
  Granularity,
  GroupBy,
  HourTariff,
  RevenueResponse,
  Role,
  ServiceStatus,
  Transaction,
  User,
} from './domain';

export interface AuthScope {
  role: Role;
  ownerId?: string | null;
}

export interface AnalyticsOptions {
  from: string;
  to: string;
  granularity: Granularity;
  groupBy: GroupBy;
  /** Фильтр по станциям (chargeBoxId). Пусто — все станции. */
  stationIds?: string[];
}

export interface Owner {
  ownerId: string;
  name: string;
}

/** Кошелёк пользователя (payment-service BalanceDto). */
export interface UserBalance {
  userId: string;
  balance: number;
  booking: boolean;
}

/** Нормализованный аккаунт: единый вид для мок- и реального входа. */
export interface Account {
  role: Role;
  ownerId?: string | null;
  email: string;
  firstName?: string | null;
  lastName?: string | null;
  id?: number;
  keycloakId?: string | null;
}

export interface DataApi {
  getStations(scope: AuthScope): Promise<ChargeBox[]>;
  getStation(scope: AuthScope, chargeBoxId: string): Promise<ChargeBox | undefined>;
  createStation(scope: AuthScope, data: Partial<ChargeBox>): Promise<ChargeBox>;
  updateStation(scope: AuthScope, id: number, data: Partial<ChargeBox>): Promise<ChargeBox>;
  deleteStation(scope: AuthScope, id: number): Promise<void>;
  setServiceStatus(scope: AuthScope, chargeBoxId: string, status: ServiceStatus): Promise<ChargeBox>;
  getConnectors(scope: AuthScope, chargeBoxId?: string): Promise<Connector[]>;
  createConnector(scope: AuthScope, data: Partial<Connector>): Promise<Connector>;
  updateConnector(scope: AuthScope, id: number, data: Partial<Connector>): Promise<Connector>;
  deleteConnector(scope: AuthScope, id: number): Promise<void>;
  getBookings(scope: AuthScope): Promise<Booking[]>;
  getTransactions(scope: AuthScope): Promise<Transaction[]>;
  getAddresses(): Promise<Address[]>;
  getConnectorTypes(): Promise<ConnectorType[]>;
  getOwners(): Promise<Owner[]>;
  getUsers(scope: AuthScope): Promise<User[]>;
  changeUserRole(scope: AuthScope, id: number, role: Role): Promise<User>;
  setUserActive(scope: AuthScope, id: number, active: boolean): Promise<User>;
  getUserBalance(scope: AuthScope, keycloakId: string): Promise<UserBalance | null>;
  topUpUser(scope: AuthScope, keycloakId: string, amount: number): Promise<UserBalance>;
  getHourlyTariffs(scope: AuthScope, stationId: string): Promise<HourTariff[]>;
  saveHourlyTariffs(scope: AuthScope, stationId: string, tariffs: HourTariff[]): Promise<void>;
  getEnergyAnalytics(scope: AuthScope, opts: AnalyticsOptions): Promise<EnergyResponse>;
  getRevenueAnalytics(scope: AuthScope, opts: AnalyticsOptions): Promise<RevenueResponse>;
  getBookingAnalytics(scope: AuthScope, opts: AnalyticsOptions): Promise<BookingAnalyticsResponse>;
  /** Перезагрузка кэша Redis из state-updater-service (ADMIN/SPECIALIST). Возвращает текст ответа. */
  reloadState(scope: AuthScope): Promise<string>;
}

export interface AuthProvider {
  login(email: string, password: string): Promise<Account>;
  logout(): void;
  /** Восстановить аккаунт из сохранённой сессии (или null). */
  restore(): Account | null;
  /** Демо-переключение роли доступно только в мок-режиме. */
  canImpersonate: boolean;
  impersonate?: (role: Role) => Account | null;
}
