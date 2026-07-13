/**
 * Мок-реализация API. Повторяет контракты бэкенда и применяет ролевой скоуп
 * (CONTRACTOR видит только свои станции). В Фазе 2 заменяется на HTTP-клиент
 * к api-gateway без изменения сигнатур.
 */
import { seededRandom, sleep } from '@/lib/utils';
import type {
  Address,
  BookingAnalyticsResponse,
  BookingDataPoint,
  ChargeBox,
  Connector,
  ConnectorType,
  EnergyDataPoint,
  EnergyResponse,
  Granularity,
  GroupBy,
  HourTariff,
  RevenueDataPoint,
  RevenueResponse,
  Role,
  User,
} from '@/types/domain';
import type { AnalyticsOptions, AuthScope, UserBalance } from '@/types/api';
import {
  addresses,
  bookings,
  chargeBoxes,
  connectors,
  connectorTypes,
  DEMO_CONTRACTOR_OWNER_ID,
  OWNERS,
  ownerByStation,
  transactions,
  users,
} from './seed';

const isScoped = (scope: AuthScope) => scope.role === 'CONTRACTOR';
const latency = () => sleep(180 + Math.random() * 320);

/** Почасовые тарифы по станции (демо-хранилище в памяти). */
const hourlyTariffs = new Map<string, HourTariff[]>();

function scopeStations(scope: AuthScope): ChargeBox[] {
  if (!isScoped(scope)) return chargeBoxes;
  return chargeBoxes.filter((c) => c.ownerId === scope.ownerId);
}

function ownedStationIds(scope: AuthScope): Set<string> {
  return new Set(scopeStations(scope).map((c) => c.chargeBoxId));
}

/* ------------------------------ Демо-аккаунты ------------------------------ */

export interface DemoAccount {
  email: string;
  password: string;
  role: Role;
  user: User;
  ownerId?: string;
}

const findUserByRole = (role: Role) => users.find((u) => u.role === role)!;

export const DEMO_ACCOUNTS: DemoAccount[] = [
  { email: 'admin@batenergy.kg', password: 'admin', role: 'ADMIN', user: findUserByRole('ADMIN') },
  { email: 'specialist@batenergy.kg', password: 'spec', role: 'SPECIALIST', user: findUserByRole('SPECIALIST') },
  {
    email: 'operator@batenergy.kg',
    password: 'operator',
    role: 'CONTRACTOR',
    ownerId: DEMO_CONTRACTOR_OWNER_ID,
    user: { ...findUserByRole('CONTRACTOR'), firstName: 'BatEnergy', lastName: 'Operator' },
  },
];

/* ------------------------------ API ------------------------------ */

export const mockApi = {
  async login(email: string, password: string): Promise<DemoAccount> {
    await latency();
    const acc = DEMO_ACCOUNTS.find(
      (a) => a.email.toLowerCase() === email.trim().toLowerCase() && a.password === password,
    );
    if (!acc) throw new Error('Неверный email или пароль');
    return acc;
  },

  async getStations(scope: AuthScope): Promise<ChargeBox[]> {
    await latency();
    return scopeStations(scope).slice().sort((a, b) => a.id - b.id);
  },

  async getStation(scope: AuthScope, chargeBoxId: string): Promise<ChargeBox | undefined> {
    await latency();
    return scopeStations(scope).find((c) => c.chargeBoxId === chargeBoxId);
  },

  async createStation(scope: AuthScope, data: Partial<ChargeBox>): Promise<ChargeBox> {
    await latency();
    const id = Math.max(...chargeBoxes.map((c) => c.id)) + 1;
    const owner = isScoped(scope)
      ? OWNERS.find((o) => o.ownerId === scope.ownerId)
      : OWNERS.find((o) => o.ownerId === data.ownerId) ?? OWNERS[0];
    const address = addresses.find((a) => a.id === data.addressId);
    const created: ChargeBox = {
      id,
      chargeBoxId: data.chargeBoxId ?? `CP_${String(id).padStart(3, '0')}`,
      ocppProtocol: data.ocppProtocol ?? 'ocpp1.6',
      chargePointVendor: data.chargePointVendor ?? null,
      chargePointModel: data.chargePointModel ?? null,
      chargePointSerialNumber: data.chargePointSerialNumber ?? null,
      chargeBoxSerialNumber: data.chargeBoxSerialNumber ?? null,
      firmwareVersion: data.firmwareVersion ?? null,
      iccid: null,
      imsi: null,
      meterType: data.meterType ?? 'DC',
      meterSerialNumber: null,
      ocppTag: data.ocppTag ?? null,
      createdAt: new Date().toISOString(),
      ownerId: owner?.ownerId ?? null,
      ownerName: owner?.name,
      power: data.power ?? '60',
      kwCost: data.kwCost ?? 14,
      bookingMinuteCost: data.bookingMinuteCost ?? 0,
      addressId: data.addressId ?? null,
      addressName: address?.addressName,
      online: false,
      serviceStatus: 'IN_SERVICE',
      connectorCount: 0,
    };
    chargeBoxes.push(created);
    return created;
  },

  async updateStation(scope: AuthScope, id: number, data: Partial<ChargeBox>): Promise<ChargeBox> {
    await latency();
    const cb = chargeBoxes.find((c) => c.id === id);
    if (!cb) throw new Error('Станция не найдена');
    if (isScoped(scope) && cb.ownerId !== scope.ownerId) throw new Error('Нет доступа');
    Object.assign(cb, data);
    if (data.addressId != null) cb.addressName = addresses.find((a) => a.id === data.addressId)?.addressName;
    return cb;
  },

  async deleteStation(scope: AuthScope, id: number): Promise<void> {
    await latency();
    const idx = chargeBoxes.findIndex((c) => c.id === id);
    if (idx < 0) return;
    if (isScoped(scope) && chargeBoxes[idx].ownerId !== scope.ownerId) throw new Error('Нет доступа');
    chargeBoxes.splice(idx, 1);
  },

  async setServiceStatus(scope: AuthScope, chargeBoxId: string, status: ChargeBox['serviceStatus']): Promise<ChargeBox> {
    const cb = chargeBoxes.find((c) => c.chargeBoxId === chargeBoxId);
    if (!cb) throw new Error('Станция не найдена');
    return this.updateStation(scope, cb.id, { serviceStatus: status });
  },

  async getConnectors(scope: AuthScope, chargeBoxId?: string): Promise<Connector[]> {
    await latency();
    const owned = ownedStationIds(scope);
    return connectors.filter(
      (c) => owned.has(c.chargeBoxId) && (!chargeBoxId || c.chargeBoxId === chargeBoxId),
    );
  },

  async createConnector(_scope: AuthScope, data: Partial<Connector>): Promise<Connector> {
    await latency();
    const id = Math.max(0, ...connectors.map((c) => c.id)) + 1;
    const type = connectorTypes.find((t) => t.id === data.connectorTypeId);
    const created: Connector = {
      id,
      chargeBoxId: data.chargeBoxId ?? '',
      connectorId: data.connectorId ?? 1,
      info: data.info ?? null,
      createdAt: new Date().toISOString(),
      vendorId: data.vendorId ?? null,
      status: data.status ?? 'Available',
      connectorTypeId: data.connectorTypeId ?? null,
      connectorTypeName: type?.connectorTypeName,
      connectorTypeCode: type?.code,
      power: null,
    };
    connectors.push(created);
    if (type) type.connectorsCount += 1;
    return created;
  },

  async updateConnector(_scope: AuthScope, id: number, data: Partial<Connector>): Promise<Connector> {
    await latency();
    const c = connectors.find((x) => x.id === id);
    if (!c) throw new Error('Коннектор не найден');
    Object.assign(c, data);
    if (data.connectorTypeId != null) {
      const type = connectorTypes.find((t) => t.id === data.connectorTypeId);
      c.connectorTypeName = type?.connectorTypeName;
      c.connectorTypeCode = type?.code;
    }
    return c;
  },

  async deleteConnector(_scope: AuthScope, id: number): Promise<void> {
    await latency();
    const idx = connectors.findIndex((x) => x.id === id);
    if (idx >= 0) connectors.splice(idx, 1);
  },

  async getBookings(scope: AuthScope): Promise<import('@/types/domain').Booking[]> {
    await latency();
    const owned = ownedStationIds(scope);
    return bookings
      .filter((b) => owned.has(b.stationId))
      .slice()
      .sort((a, b) => +new Date(b.startedAt) - +new Date(a.startedAt));
  },

  async getTransactions(scope: AuthScope): Promise<import('@/types/domain').Transaction[]> {
    await latency();
    const owned = ownedStationIds(scope);
    return transactions
      .filter((t) => owned.has(t.chargeBoxId))
      .slice()
      .sort((a, b) => +new Date(b.startTimestamp) - +new Date(a.startTimestamp));
  },

  async getAddresses(): Promise<Address[]> {
    await latency();
    return addresses;
  },

  async getConnectorTypes(): Promise<ConnectorType[]> {
    await latency();
    return connectorTypes;
  },

  async getOwners() {
    await latency();
    return OWNERS;
  },

  // Только ADMIN — список пользователей.
  async getUsers(scope: AuthScope): Promise<User[]> {
    await latency();
    if (scope.role !== 'ADMIN') throw new Error('Доступ только для администратора');
    return users.slice().sort((a, b) => a.id - b.id);
  },

  async changeUserRole(scope: AuthScope, id: number, role: Role): Promise<User> {
    await latency();
    if (scope.role !== 'ADMIN') throw new Error('Доступ только для администратора');
    const u = users.find((x) => x.id === id);
    if (!u) throw new Error('Пользователь не найден');
    u.role = role;
    return u;
  },

  async setUserActive(scope: AuthScope, id: number, active: boolean): Promise<User> {
    await latency();
    if (scope.role !== 'ADMIN') throw new Error('Доступ только для администратора');
    const u = users.find((x) => x.id === id);
    if (!u) throw new Error('Пользователь не найден');
    u.active = active;
    return u;
  },

  async getUserBalance(_scope: AuthScope, keycloakId: string): Promise<UserBalance | null> {
    await latency();
    const u = users.find((x) => x.keycloakId === keycloakId);
    if (!u) return null;
    return { userId: keycloakId, balance: u.balance ?? 0, booking: false };
  },

  async topUpUser(_scope: AuthScope, keycloakId: string, amount: number): Promise<UserBalance> {
    await latency();
    const u = users.find((x) => x.keycloakId === keycloakId);
    if (!u) throw new Error('Пользователь не найден');
    u.balance = (u.balance ?? 0) + amount;
    return { userId: keycloakId, balance: u.balance, booking: false };
  },

  async getHourlyTariffs(_scope: AuthScope, stationId: string): Promise<HourTariff[]> {
    await latency();
    return hourlyTariffs.get(stationId) ?? [];
  },

  async saveHourlyTariffs(_scope: AuthScope, stationId: string, tariffs: HourTariff[]): Promise<void> {
    await latency();
    hourlyTariffs.set(stationId, tariffs);
  },

  /* ---------------------------- Аналитика ---------------------------- */

  async getEnergyAnalytics(scope: AuthScope, opts: AnalyticsOptions): Promise<EnergyResponse> {
    await latency();
    const buckets = buildBuckets(opts);
    const seriesDefs = seriesDefinitions(scope, opts.groupBy);
    const series = seriesDefs.map((def) => {
      const points: EnergyDataPoint[] = buckets.map((periodStart) => {
        const base = wave(periodStart, def.weight, opts.granularity);
        const energyKwh = round1(base * 42);
        const sessions = Math.max(1, Math.round(base * 6));
        return {
          periodStart,
          energyKwh,
          sessions,
          avgDurationMinutes: round1(35 + wave(periodStart, def.weight + 3, opts.granularity) * 25),
          revenue: Math.round(energyKwh * 14),
        };
      });
      return { groupKey: def.key, label: def.label, points };
    });
    const all = series.flatMap((s) => s.points);
    const totalEnergy = round1(sum(all.map((p) => p.energyKwh)));
    const totalSessions = sum(all.map((p) => p.sessions));
    return {
      from: opts.from,
      to: opts.to,
      granularity: opts.granularity,
      groupBy: opts.groupBy,
      summary: {
        totalEnergyKwh: totalEnergy,
        totalSessions,
        avgEnergyPerSessionKwh: round1(totalEnergy / Math.max(1, totalSessions)),
        avgSessionDurationMinutes: round1(avg(all.map((p) => p.avgDurationMinutes))),
        totalRevenue: Math.round(sum(all.map((p) => p.revenue))),
        uniqueStations: scopeStations(scope).length,
        uniqueUsers: Math.round(totalSessions * 0.6),
      },
      series,
    };
  },

  async getRevenueAnalytics(scope: AuthScope, opts: AnalyticsOptions): Promise<RevenueResponse> {
    await latency();
    const buckets = buildBuckets(opts);
    const seriesDefs = seriesDefinitions(scope, opts.groupBy);
    const series = seriesDefs.map((def) => {
      const points: RevenueDataPoint[] = buckets.map((periodStart) => {
        const base = wave(periodStart, def.weight, opts.granularity);
        const chargingRevenue = Math.round(base * 620);
        const bookingRevenue = Math.round(base * 130);
        return {
          periodStart,
          totalRevenue: chargingRevenue + bookingRevenue,
          chargingRevenue,
          bookingRevenue,
          chargingSessions: Math.max(1, Math.round(base * 6)),
          bookingCount: Math.max(0, Math.round(base * 2)),
        };
      });
      return { groupKey: def.key, label: def.label, points };
    });
    const all = series.flatMap((s) => s.points);
    return {
      from: opts.from,
      to: opts.to,
      granularity: opts.granularity,
      groupBy: opts.groupBy,
      summary: {
        totalRevenue: Math.round(sum(all.map((p) => p.totalRevenue))),
        chargingRevenue: Math.round(sum(all.map((p) => p.chargingRevenue))),
        bookingRevenue: Math.round(sum(all.map((p) => p.bookingRevenue))),
        chargingSessions: sum(all.map((p) => p.chargingSessions)),
        bookingCount: sum(all.map((p) => p.bookingCount)),
      },
      series,
    };
  },

  async getBookingAnalytics(scope: AuthScope, opts: AnalyticsOptions): Promise<BookingAnalyticsResponse> {
    await latency();
    const buckets = buildBuckets(opts);
    const seriesDefs = seriesDefinitions(scope, opts.groupBy);
    const series = seriesDefs.map((def) => {
      const points: BookingDataPoint[] = buckets.map((periodStart) => {
        const base = wave(periodStart, def.weight, opts.granularity);
        const bookingsCount = Math.max(0, Math.round(base * 3));
        const totalMinutes = bookingsCount * (30 + Math.round(wave(periodStart, def.weight + 1, opts.granularity) * 40));
        return {
          periodStart,
          bookings: bookingsCount,
          totalMinutes,
          avgDurationMinutes: bookingsCount ? round1(totalMinutes / bookingsCount) : 0,
          revenue: Math.round(totalMinutes * 3),
        };
      });
      return { groupKey: def.key, label: def.label, points };
    });
    const all = series.flatMap((s) => s.points);
    const totalBookings = sum(all.map((p) => p.bookings));
    const totalMinutes = sum(all.map((p) => p.totalMinutes));
    return {
      from: opts.from,
      to: opts.to,
      granularity: opts.granularity,
      groupBy: opts.groupBy,
      summary: {
        totalBookings,
        totalMinutes,
        avgDurationMinutes: totalBookings ? round1(totalMinutes / totalBookings) : 0,
        totalRevenue: Math.round(sum(all.map((p) => p.revenue))),
      },
      series,
    };
  },
};

/* ------------------------------ Аналитика: утилиты ------------------------------ */

const GRAN_MS: Record<Granularity, number> = {
  HOUR: 3_600_000,
  DAY: 86_400_000,
  WEEK: 604_800_000,
  MONTH: 2_592_000_000,
  YEAR: 31_536_000_000,
};

function buildBuckets(opts: AnalyticsOptions): string[] {
  const step = GRAN_MS[opts.granularity];
  const start = +new Date(opts.from);
  const end = +new Date(opts.to);
  const out: string[] = [];
  for (let t = start; t <= end && out.length < 400; t += step) out.push(new Date(t).toISOString());
  return out;
}

interface SeriesDef {
  key: string;
  label: string;
  weight: number;
}

function seriesDefinitions(scope: AuthScope, groupBy: GroupBy): SeriesDef[] {
  if (groupBy === 'TOTAL') return [{ key: 'TOTAL', label: 'Все станции', weight: 1 }];
  if (groupBy === 'OWNER') {
    const owners = isScoped(scope)
      ? OWNERS.filter((o) => o.ownerId === scope.ownerId)
      : OWNERS;
    return owners.map((o, i) => ({ key: o.ownerId, label: o.name, weight: 0.5 + i * 0.35 }));
  }
  // STATION — топ станций скоупа
  const stations = scopeStations(scope).slice(0, 6);
  return stations.map((s, i) => ({
    key: s.chargeBoxId,
    label: s.addressName ?? s.chargeBoxId,
    weight: 0.4 + i * 0.2,
  }));
}

/** Волна с суточной/недельной сезонностью + детерминированный шум. */
function wave(iso: string, weight: number, gran: Granularity): number {
  const d = new Date(iso);
  const hour = d.getHours();
  const dow = d.getDay();
  const dayFactor = gran === 'HOUR' ? 0.55 + 0.45 * Math.sin(((hour - 7) / 24) * Math.PI * 2) : 1;
  const weekend = dow === 0 || dow === 6 ? 0.8 : 1;
  const noise = seededRandom(Math.floor(+d / 3_600_000) + Math.round(weight * 100))();
  return Math.max(0.1, weight * dayFactor * weekend * (0.7 + noise * 0.6));
}

const sum = (a: number[]) => a.reduce((x, y) => x + y, 0);
const avg = (a: number[]) => (a.length ? sum(a) / a.length : 0);
const round1 = (n: number) => Math.round(n * 10) / 10;

export { ownerByStation };
