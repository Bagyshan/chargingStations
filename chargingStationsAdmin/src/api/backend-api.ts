/**
 * Реальная реализация [DataApi] и авторизации поверх api-gateway.
 * Маппит DTO бэкенда (contractor-admin / user-service / station-controll) в
 * доменные модели и делает лёгкое обогащение (адрес, тип коннектора, online).
 */
import { SERVICE } from './config';
import { ApiError, request } from './http';
import { parseDate } from '@/lib/format';
import { pickRole, rolesOf, subOf } from './jwt';
import { tokenStore } from './token-store';
import type { Account, AnalyticsOptions, AuthProvider, DataApi, Owner, UserBalance } from '@/types/api';
import type {
  Address,
  Booking,
  BookingAnalyticsResponse,
  BookingStatus,
  ChargeBox,
  Connector,
  ConnectorStatus,
  ConnectorType,
  EnergyResponse,
  HourTariff,
  RevenueResponse,
  Role,
  ServiceStatus,
  Transaction,
  TransactionStatus,
  User,
} from '@/types/domain';

/* ------------------------------ Сырые формы бэкенда ------------------------------ */

interface RawChargeBox {
  id: number;
  chargeBoxId: string;
  ocppProtocol?: string;
  chargePointVendor?: string;
  chargePointModel?: string;
  chargePointSerialNumber?: string;
  chargeBoxSerialNumber?: string;
  firmwareVersion?: string;
  iccid?: string;
  imsi?: string;
  meterType?: string;
  meterSerialNumber?: string;
  ocppTag?: string;
  createdAt?: string;
  ownerId?: string;
  power?: string;
  kwCost?: number;
  bookingMinuteCost?: number;
  addressId?: number;
}

interface RawStation {
  id: string;
  chargeBoxId?: string;
  ownerId?: string;
  serviceStatus?: string;
  online?: boolean;
  geolocation?: { latitude?: number; longitude?: number };
  address?: { id?: number; addressName?: string };
  connectors?: { connectorId: number; status: string }[];
}

interface RawConnector {
  id: number;
  chargeBoxId: string;
  connectorId: number;
  info?: string;
  createdAt?: string;
  vendorId?: string;
  status: string;
  connectorTypeId?: number;
}

interface RawBooking {
  id: number;
  bookingId: string;
  userId: string;
  stationId: string;
  connectorId: number;
  pricePerMinute?: number;
  totalSum?: number;
  totalMinutes?: number;
  startedAt: string;
  endedAt?: string;
  status: string;
  createdAt: string;
}

interface RawAdminBooking {
  bookingId: string;
  userId: string;
  stationId: string;
  connectorId: number;
  status: string;
  pricePerMinute?: number;
  totalMinutes?: number;
  totalSum?: number;
  startedAt: string;
  endedAt?: string;
  createdAt: string;
}

interface RawTransaction {
  id: number;
  transactionId: number;
  chargeBoxId: string;
  connectorId: number;
  startTimestamp: string;
  startValue?: number;
  stopTimestamp?: string;
  stopValue?: number;
  transactionValue?: number;
  totalSum?: number;
  status: string;
  reason?: string;
  userId: string;
  createdAt: string;
  updatedAt: string;
}

interface RawUser {
  id: number;
  keycloakId?: string;
  email: string;
  phone?: string;
  firstName?: string;
  lastName?: string;
  role: string;
  emailVerified?: boolean;
  phoneVerified?: boolean;
  active?: boolean;
  createdAt?: string | number[];
  lastLoginAt?: string | number[];
}

interface RawAddress {
  id: number;
  addressName: string;
  chargeBoxCount?: number;
}

interface RawConnectorType {
  id: number;
  connectorTypeName: string;
  code?: string;
  connectorsCount?: number;
}

/* ------------------------------ Маппинг ------------------------------ */

const CA = SERVICE.contractorAdmin;
const asStatus = (s: string) => s as ConnectorStatus;

function mapBox(b: RawChargeBox): ChargeBox {
  return {
    id: b.id,
    chargeBoxId: b.chargeBoxId,
    ocppProtocol: b.ocppProtocol ?? null,
    chargePointVendor: b.chargePointVendor ?? null,
    chargePointModel: b.chargePointModel ?? null,
    chargePointSerialNumber: b.chargePointSerialNumber ?? null,
    chargeBoxSerialNumber: b.chargeBoxSerialNumber ?? null,
    firmwareVersion: b.firmwareVersion ?? null,
    iccid: b.iccid ?? null,
    imsi: b.imsi ?? null,
    meterType: b.meterType ?? null,
    meterSerialNumber: b.meterSerialNumber ?? null,
    ocppTag: b.ocppTag ?? null,
    createdAt: b.createdAt ?? new Date().toISOString(),
    ownerId: b.ownerId ?? null,
    power: b.power ?? null,
    kwCost: b.kwCost ?? null,
    bookingMinuteCost: b.bookingMinuteCost ?? null,
    addressId: b.addressId ?? null,
  };
}

async function stationMap(): Promise<Map<string, RawStation>> {
  try {
    const stations = await request<RawStation[]>(`${SERVICE.stationControll}/api/stations`);
    return new Map(stations.map((s) => [s.chargeBoxId ?? s.id, s]));
  } catch {
    return new Map();
  }
}

/* ------------------------------ DataApi ------------------------------ */

export const backendApi: DataApi = {
  async getStations(): Promise<ChargeBox[]> {
    const [boxes, stations] = await Promise.all([
      request<RawChargeBox[]>(`${CA}/api/charge-boxes`),
      stationMap(),
    ]);
    return boxes.map((b) => {
      const s = stations.get(b.chargeBoxId);
      return {
        ...mapBox(b),
        online: s?.online,
        serviceStatus: (s?.serviceStatus as ServiceStatus) ?? undefined,
        latitude: s?.geolocation?.latitude,
        longitude: s?.geolocation?.longitude,
        addressName: s?.address?.addressName,
        connectorCount: s?.connectors?.length,
        ownerName: b.ownerId ?? undefined,
      };
    });
  },

  async getStation(_scope, chargeBoxId): Promise<ChargeBox | undefined> {
    const [box, stations] = await Promise.all([
      request<RawChargeBox | null>(`${CA}/api/charge-boxes/by-charge-box-id/${chargeBoxId}`).catch(
        () => null,
      ),
      stationMap(),
    ]);
    if (!box) return undefined;
    const s = stations.get(chargeBoxId);
    return {
      ...mapBox(box),
      online: s?.online,
      serviceStatus: (s?.serviceStatus as ServiceStatus) ?? undefined,
      latitude: s?.geolocation?.latitude,
      longitude: s?.geolocation?.longitude,
      addressName: s?.address?.addressName,
      connectorCount: s?.connectors?.length,
      ownerName: box.ownerId ?? undefined,
    };
  },

  async createStation(_scope, data): Promise<ChargeBox> {
    const raw = await request<RawChargeBox>(`${CA}/api/charge-boxes`, {
      method: 'POST',
      body: toChargeBoxRequest(data),
    });
    return mapBox(raw);
  },

  async updateStation(_scope, id, data): Promise<ChargeBox> {
    const raw = await request<RawChargeBox>(`${CA}/api/charge-boxes/${id}`, {
      method: 'PATCH',
      body: toChargeBoxRequest(data),
    });
    return mapBox(raw);
  },

  async deleteStation(_scope, id): Promise<void> {
    await request<void>(`${CA}/api/charge-boxes/${id}`, { method: 'DELETE' });
  },

  async setServiceStatus(scope, chargeBoxId, status): Promise<ChargeBox> {
    await request<RawStation>(`${SERVICE.stationControll}/api/stations/${chargeBoxId}/service-status`, {
      method: 'PATCH',
      body: { serviceStatus: status },
    });
    const updated = await this.getStation(scope, chargeBoxId);
    if (!updated) throw new ApiError(404, 'Станция не найдена');
    return updated;
  },

  async getConnectors(_scope, chargeBoxId): Promise<Connector[]> {
    const [conns, types] = await Promise.all([
      request<RawConnector[]>(
        chargeBoxId
          ? `${CA}/api/connectors/by-charge-box/${chargeBoxId}`
          : `${CA}/api/connectors`,
      ),
      connectorTypeMap(),
    ]);
    return conns.map((c) => mapConnector(c, types));
  },

  async createConnector(_scope, data): Promise<Connector> {
    const raw = await request<RawConnector>(`${CA}/api/connectors`, {
      method: 'POST',
      body: toConnectorRequest(data),
    });
    return mapConnector(raw, await connectorTypeMap());
  },

  async updateConnector(_scope, id, data): Promise<Connector> {
    const raw = await request<RawConnector>(`${CA}/api/connectors/${id}`, {
      method: 'PATCH',
      body: toConnectorRequest(data),
    });
    return mapConnector(raw, await connectorTypeMap());
  },

  async deleteConnector(_scope, id): Promise<void> {
    await request<void>(`${CA}/api/connectors/${id}`, { method: 'DELETE' });
  },

  async getBookings(scope): Promise<Booking[]> {
    const stations = await stationMap();
    const addr = (sid: string) => stations.get(sid)?.address?.addressName;

    // ADMIN/SPECIALIST — все брони из booking-service (мирор contractor-admin
    // пуст; booking-service /all отдаёт полный список). CONTRACTOR — мирор со
    // скоупом по владельцу станций.
    if (scope.role === 'ADMIN' || scope.role === 'SPECIALIST') {
      const raw = await request<RawAdminBooking[]>(`${SERVICE.booking}/api/bookings/all`);
      return raw.map((b, i) => ({
        id: i + 1,
        bookingId: b.bookingId,
        userId: b.userId,
        stationId: b.stationId,
        connectorId: b.connectorId,
        pricePerMinute: b.pricePerMinute ?? 0,
        totalSum: b.totalSum ?? 0,
        totalMinutes: b.totalMinutes ?? 0,
        startedAt: b.startedAt,
        endedAt: b.endedAt ?? null,
        status: b.status as BookingStatus,
        createdAt: b.createdAt,
        addressName: addr(b.stationId),
      }));
    }

    const raw = await request<RawBooking[]>(`${CA}/api/bookings`);
    return raw
      .map((b) => ({
        id: b.id,
        bookingId: b.bookingId,
        userId: b.userId,
        stationId: b.stationId,
        connectorId: b.connectorId,
        pricePerMinute: b.pricePerMinute ?? 0,
        totalSum: b.totalSum ?? 0,
        totalMinutes: b.totalMinutes ?? 0,
        startedAt: b.startedAt,
        endedAt: b.endedAt ?? null,
        status: b.status as BookingStatus,
        createdAt: b.createdAt,
        addressName: addr(b.stationId),
      }))
      .sort((a, b) => +new Date(b.startedAt) - +new Date(a.startedAt));
  },

  async getTransactions(): Promise<Transaction[]> {
    const [raw, stations] = await Promise.all([
      request<RawTransaction[]>(`${CA}/api/transactions`),
      stationMap(),
    ]);
    return raw
      .map((t) => ({
        id: t.id,
        transactionId: t.transactionId,
        chargeBoxId: t.chargeBoxId,
        connectorId: t.connectorId,
        startTimestamp: t.startTimestamp,
        startValue: t.startValue ?? 0,
        stopTimestamp: t.stopTimestamp ?? null,
        stopValue: t.stopValue ?? null,
        // transaction_value приходит в Вт·ч — в модели держим кВт·ч (как в моке).
        transactionValue: (t.transactionValue ?? 0) / 1000,
        totalSum: t.totalSum ?? 0,
        status: t.status as TransactionStatus,
        reason: t.reason ?? null,
        userId: t.userId,
        createdAt: t.createdAt,
        updatedAt: t.updatedAt,
        addressName: stations.get(t.chargeBoxId)?.address?.addressName,
      }))
      .sort((a, b) => +new Date(b.startTimestamp) - +new Date(a.startTimestamp));
  },

  async getAddresses(): Promise<Address[]> {
    const raw = await request<RawAddress[]>(`${CA}/api/external/addresses`);
    return raw.map((a) => ({ id: a.id, addressName: a.addressName, chargeBoxCount: a.chargeBoxCount ?? 0 }));
  },

  async getConnectorTypes(): Promise<ConnectorType[]> {
    const raw = await request<RawConnectorType[]>(`${CA}/api/external/connector-types`);
    return raw.map((t) => ({
      id: t.id,
      connectorTypeName: t.connectorTypeName,
      code: t.code ?? '',
      connectorsCount: t.connectorsCount ?? 0,
    }));
  },

  async getOwners(): Promise<Owner[]> {
    const boxes = await request<RawChargeBox[]>(`${CA}/api/charge-boxes`).catch(() => []);
    const ids = [...new Set(boxes.map((b) => b.ownerId).filter((x): x is string => !!x))];
    return ids.map((ownerId) => ({ ownerId, name: ownerId }));
  },

  async getUsers(): Promise<User[]> {
    const raw = await request<RawUser[]>(`${SERVICE.user}/api/v1/users/all`);
    return raw.map(mapUser);
  },

  async changeUserRole(_scope, id, role): Promise<User> {
    const raw = await request<RawUser>(`${SERVICE.user}/api/v1/admin/users/${id}/role`, {
      method: 'PUT',
      body: { role },
      unwrap: true,
    });
    return mapUser(raw);
  },

  async setUserActive(_scope, id, active): Promise<User> {
    await request<unknown>(
      `${SERVICE.user}/api/v1/admin/users/${id}/${active ? 'activate' : 'deactivate'}`,
      { method: 'POST' },
    );
    const raw = await request<RawUser>(`${SERVICE.user}/api/v1/users/${id}`, { unwrap: true });
    return mapUser(raw);
  },

  async getUserBalance(_scope, keycloakId): Promise<UserBalance | null> {
    try {
      const b = await request<RawBalance>(`${SERVICE.payment}/api/v1/balance/${keycloakId}`);
      return mapBalance(b);
    } catch (e) {
      if (e instanceof ApiError && e.status === 404) return null;
      throw e;
    }
  },

  async topUpUser(_scope, keycloakId, amount): Promise<UserBalance> {
    const b = await request<RawBalance>(
      `${SERVICE.payment}/api/v1/admin/balance/${keycloakId}/top-up`,
      { method: 'POST', body: { amount } },
    );
    return mapBalance(b);
  },

  async getHourlyTariffs(_scope, stationId): Promise<HourTariff[]> {
    const raw = await request<{ hour: number; kwCost: number; bookingMinuteCost: number }[]>(
      `${CA}/api/stations/${stationId}/hourly-tariffs`,
    );
    return raw.map((t) => ({
      hour: t.hour,
      kwCost: t.kwCost ?? 0,
      bookingMinuteCost: t.bookingMinuteCost ?? 0,
    }));
  },

  async saveHourlyTariffs(_scope, stationId, tariffs): Promise<void> {
    await request<void>(`${CA}/api/stations/${stationId}/hourly-tariffs`, {
      method: 'PUT',
      body: { tariffs },
    });
  },

  async getEnergyAnalytics(_scope, opts): Promise<EnergyResponse> {
    return request<EnergyResponse>(`${CA}/api/analytics/energy?${qs(opts)}`);
  },

  async getRevenueAnalytics(_scope, opts): Promise<RevenueResponse> {
    const r = await request<RawRevenue>(`${CA}/api/analytics/revenue?${qs(opts)}`);
    return {
      ...r,
      summary: {
        totalRevenue: r.summary.totalRevenue,
        chargingRevenue: r.summary.chargingRevenue,
        bookingRevenue: r.summary.bookingRevenue,
        chargingSessions: r.summary.totalChargingSessions ?? 0,
        bookingCount: r.summary.totalBookings ?? 0,
      },
    };
  },

  async getBookingAnalytics(_scope, opts): Promise<BookingAnalyticsResponse> {
    return request<BookingAnalyticsResponse>(`${CA}/api/analytics/bookings?${qs(opts)}`);
  },

  async reloadState(_scope): Promise<string> {
    // POST /state-updater/api/state-updater/reload → перегрев кэша Redis из БД.
    // Ответ — plain text ("Data reload initiated successfully"), поэтому raw.
    return request<string>(`${SERVICE.stateUpdater}/api/state-updater/reload`, {
      method: 'POST',
      raw: true,
    });
  },
};

interface RawRevenue extends Omit<RevenueResponse, 'summary'> {
  summary: {
    totalRevenue: number;
    chargingRevenue: number;
    bookingRevenue: number;
    totalChargingSessions?: number;
    totalBookings?: number;
  };
}

function mapUser(u: RawUser): User {
  return {
    id: u.id,
    keycloakId: u.keycloakId,
    email: u.email,
    phone: u.phone ?? null,
    firstName: u.firstName ?? null,
    lastName: u.lastName ?? null,
    role: (u.role as Role) ?? 'USER',
    emailVerified: !!u.emailVerified,
    phoneVerified: !!u.phoneVerified,
    active: u.active ?? true,
    createdAt: parseDate(u.createdAt)?.toISOString() ?? new Date().toISOString(),
    lastLoginAt: parseDate(u.lastLoginAt)?.toISOString() ?? null,
  };
}

// BalanceDto сериализует флаг брони как isBooking (Lombok-геттер) — читаем оба варианта.
interface RawBalance {
  userId: string;
  balance: number;
  booking?: boolean;
  isBooking?: boolean;
}

function mapBalance(b: RawBalance): UserBalance {
  return { userId: b.userId, balance: b.balance ?? 0, booking: !!(b.booking ?? b.isBooking) };
}

async function connectorTypeMap(): Promise<Map<number, RawConnectorType>> {
  try {
    const types = await request<RawConnectorType[]>(`${CA}/api/external/connector-types`);
    return new Map(types.map((t) => [t.id, t]));
  } catch {
    return new Map();
  }
}

function mapConnector(c: RawConnector, types: Map<number, RawConnectorType>): Connector {
  const t = c.connectorTypeId != null ? types.get(c.connectorTypeId) : undefined;
  return {
    id: c.id,
    chargeBoxId: c.chargeBoxId,
    connectorId: c.connectorId,
    info: c.info ?? null,
    createdAt: c.createdAt ?? new Date().toISOString(),
    vendorId: c.vendorId ?? null,
    status: asStatus(c.status),
    connectorTypeId: c.connectorTypeId ?? null,
    connectorTypeName: t?.connectorTypeName,
    connectorTypeCode: t?.code,
    power: null,
  };
}

/** Только заданные поля (undefined не отправляем — иначе затрём значения на бэке). */
function prune<T extends Record<string, unknown>>(obj: T): Partial<T> {
  return Object.fromEntries(Object.entries(obj).filter(([, v]) => v !== undefined)) as Partial<T>;
}

function toChargeBoxRequest(data: Partial<ChargeBox>) {
  return prune({
    chargeBoxId: data.chargeBoxId,
    ocppProtocol: data.ocppProtocol,
    chargePointVendor: data.chargePointVendor,
    chargePointModel: data.chargePointModel,
    chargePointSerialNumber: data.chargePointSerialNumber,
    chargeBoxSerialNumber: data.chargeBoxSerialNumber,
    firmwareVersion: data.firmwareVersion,
    meterType: data.meterType,
    meterSerialNumber: data.meterSerialNumber,
    ocppTag: data.ocppTag,
    ownerId: data.ownerId,
    power: data.power,
    kwCost: data.kwCost,
    bookingMinuteCost: data.bookingMinuteCost,
    addressId: data.addressId,
    latitude: data.latitude,
    longitude: data.longitude,
  });
}

function toConnectorRequest(data: Partial<Connector>) {
  return prune({
    chargeBoxId: data.chargeBoxId,
    connectorId: data.connectorId,
    info: data.info,
    vendorId: data.vendorId,
    status: data.status,
    connectorTypeId: data.connectorTypeId,
  });
}

function qs(opts: AnalyticsOptions): string {
  const p = new URLSearchParams({
    from: opts.from,
    to: opts.to,
    granularity: opts.granularity,
    groupBy: opts.groupBy,
  });
  if (opts.stationIds?.length) {
    const csv = opts.stationIds.join(',');
    // energy → chargeBoxIds, revenue/bookings → stationIds; лишние параметры бэк игнорирует.
    p.set('chargeBoxIds', csv);
    p.set('stationIds', csv);
  }
  return p.toString();
}

/* ------------------------------ Авторизация ------------------------------ */

interface RawAuth {
  accessToken: string;
  refreshToken: string;
  expiresIn?: number;
  user?: { id?: number; email?: string; firstName?: string; lastName?: string; role?: string };
}

function accountFromToken(fallbackEmail: string, u?: RawAuth['user']): Account {
  const access = tokenStore.access;
  const role: Role = u?.role ? (u.role as Role) : pickRole(rolesOf(access));
  const keycloakId = subOf(access);
  return {
    role,
    ownerId: role === 'CONTRACTOR' ? keycloakId : undefined,
    email: u?.email ?? fallbackEmail,
    firstName: u?.firstName ?? null,
    lastName: u?.lastName ?? null,
    id: u?.id,
    keycloakId,
  };
}

async function backendLogin(email: string, password: string): Promise<Account> {
  try {
    const auth = await request<RawAuth>(`${SERVICE.user}/api/v1/auth/login`, {
      method: 'POST',
      body: { email, password },
      auth: false,
      unwrap: true,
    });
    tokenStore.set(auth.accessToken, auth.refreshToken, auth.expiresIn ?? null);
    const account = accountFromToken(email, auth.user);
    if (account.role === 'USER') {
      tokenStore.clear();
      throw new ApiError(403, 'Нет доступа к панели: требуется роль администратора, специалиста или контрагента.');
    }
    return account;
  } catch (e) {
    if (e instanceof ApiError) {
      if (e.status === 401 || e.status === 404) throw new Error('Неверный email или пароль');
      throw new Error(e.message || 'Не удалось войти');
    }
    throw new Error('Нет связи с сервером');
  }
}

export const backendAuth: AuthProvider = {
  login: backendLogin,
  logout() {
    tokenStore.clear();
  },
  restore(): Account | null {
    if (!tokenStore.hasSession()) return null;
    const role = pickRole(rolesOf(tokenStore.access));
    if (role === 'USER') return null;
    return accountFromToken('', undefined);
  },
  canImpersonate: false,
};
