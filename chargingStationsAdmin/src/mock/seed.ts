/**
 * Детерминированные мок-данные (сид). Всё генерируется одним сидом, поэтому
 * данные стабильны между перезагрузками — удобно для дизайна и демонстрации.
 * Форма записей повторяет DTO бэкенда (см. src/types/domain.ts).
 */
import { pick, seededRandom } from '@/lib/utils';
import type {
  Address,
  Booking,
  ChargeBox,
  Connector,
  ConnectorStatus,
  ConnectorType,
  ServiceStatus,
  Transaction,
  TransactionStatus,
  User,
  Role,
} from '@/types/domain';

const rng = seededRandom(20260713);

const DAY = 86_400_000;
const nowIso = () => new Date().toISOString();
const ago = (ms: number) => new Date(Date.now() - ms).toISOString();

/* ------------------------------ Владельцы ------------------------------ */

export interface Owner {
  ownerId: string;
  name: string;
}

export const OWNERS: Owner[] = [
  { ownerId: 'own-batenergy', name: 'BatEnergy Operator' },
  { ownerId: 'own-voltpark', name: 'VoltPark' },
  { ownerId: 'own-greencharge', name: 'GreenCharge KG' },
];

/** Контрагент, под которым «залогинен» демо-пользователь роли CONTRACTOR. */
export const DEMO_CONTRACTOR_OWNER_ID = 'own-batenergy';

/* ------------------------------ Адреса ------------------------------ */

const ADDRESS_NAMES = [
  'пр. Чуй, 155',
  'ул. Ахунбаева, 97',
  'ул. Киевская, 44',
  'пр. Манаса, 40',
  'ТЦ «Азия Молл», паркинг',
  'ул. Юнусалиева, 120',
  'Южная магистраль, 12',
  'ТЦ «Bishkek Park»',
  'ул. Тыныстанова, 199',
  'Аэропорт «Манас», терминал',
];

export const addresses: Address[] = ADDRESS_NAMES.map((addressName, i) => ({
  id: i + 1,
  addressName,
  chargeBoxCount: 0, // проставим после генерации станций
}));

/* --------------------------- Типы коннекторов --------------------------- */

export const connectorTypes: ConnectorType[] = [
  { id: 1, connectorTypeName: 'CCS Combo 2', code: 'CCS2', connectorsCount: 0, maxPowerKw: 150 },
  { id: 2, connectorTypeName: 'Type 2 (Mennekes)', code: 'TYPE2', connectorsCount: 0, maxPowerKw: 22 },
  { id: 3, connectorTypeName: 'CHAdeMO', code: 'CHADEMO', connectorsCount: 0, maxPowerKw: 50 },
  { id: 4, connectorTypeName: 'GB/T', code: 'GBT', connectorsCount: 0, maxPowerKw: 60 },
  { id: 5, connectorTypeName: 'Type 1 (J1772)', code: 'TYPE1', connectorsCount: 0, maxPowerKw: 7 },
];

/* ------------------------------ Станции ------------------------------ */

const VENDORS = ['ABB', 'Siemens', 'Delta', 'Kempower', 'Alpitronic', 'Autel'];
const MODELS = ['Terra 184', 'HYC 400', 'Movitrans', 'Sicharge D', 'HPC-360', 'MaxiCharger'];
const SERVICE_STATUSES: ServiceStatus[] = ['IN_SERVICE', 'IN_SERVICE', 'IN_SERVICE', 'MAINTENANCE', 'OUT_OF_SERVICE'];
const CONNECTOR_STATUSES: ConnectorStatus[] = [
  'Available',
  'Available',
  'Charging',
  'Charging',
  'Preparing',
  'Reserved',
  'Unavailable',
  'Faulted',
];

const STATION_COUNT = 26;
const BISHKEK = { lat: 42.8746, lng: 74.5698 };

export const chargeBoxes: ChargeBox[] = [];
export const connectors: Connector[] = [];

let connectorPk = 1;

for (let i = 0; i < STATION_COUNT; i++) {
  const owner = pick(rng, OWNERS);
  const address = pick(rng, addresses);
  const powerKw = pick(rng, [22, 30, 60, 90, 120, 150, 180]);
  const online = rng() > 0.18;
  const serviceStatus = pick(rng, SERVICE_STATUSES);
  const vendor = pick(rng, VENDORS);
  const chargeBoxId = `CP_${String(i + 1).padStart(3, '0')}`;
  const nConnectors = 1 + Math.floor(rng() * 3);

  address.chargeBoxCount += 1;

  chargeBoxes.push({
    id: i + 1,
    chargeBoxId,
    ocppProtocol: pick(rng, ['ocpp1.6', 'ocpp2.0.1']),
    chargePointVendor: vendor,
    chargePointModel: pick(rng, MODELS),
    chargePointSerialNumber: `SN-${1000 + i}`,
    chargeBoxSerialNumber: `CB-${2000 + i}`,
    firmwareVersion: `${1 + Math.floor(rng() * 3)}.${Math.floor(rng() * 9)}.${Math.floor(rng() * 9)}`,
    iccid: `8996${Math.floor(rng() * 1e12)}`,
    imsi: `4370${Math.floor(rng() * 1e11)}`,
    meterType: pick(rng, ['AC', 'DC']),
    meterSerialNumber: `MTR-${3000 + i}`,
    ocppTag: `TAG_${chargeBoxId}`,
    createdAt: ago(Math.floor(rng() * 360) * DAY),
    ownerId: owner.ownerId,
    ownerName: owner.name,
    power: String(powerKw),
    kwCost: pick(rng, [11, 12, 13.5, 15, 16.5, 18]),
    bookingMinuteCost: pick(rng, [0, 0, 2, 3, 5]),
    addressId: address.id,
    addressName: address.addressName,
    online,
    serviceStatus,
    connectorCount: nConnectors,
    latitude: BISHKEK.lat + (rng() - 0.5) * 0.14,
    longitude: BISHKEK.lng + (rng() - 0.5) * 0.2,
  });

  for (let c = 1; c <= nConnectors; c++) {
    const type = pick(rng, connectorTypes);
    type.connectorsCount += 1;
    const status: ConnectorStatus =
      serviceStatus !== 'IN_SERVICE'
        ? 'Unavailable'
        : !online
          ? 'Unavailable'
          : pick(rng, CONNECTOR_STATUSES);
    connectors.push({
      id: connectorPk++,
      chargeBoxId,
      connectorId: c,
      info: null,
      createdAt: ago(Math.floor(rng() * 300) * DAY),
      vendorId: vendor,
      status,
      connectorTypeId: type.id,
      connectorTypeName: type.connectorTypeName,
      connectorTypeCode: type.code,
      power: String(Math.min(powerKw, type.maxPowerKw ?? powerKw)),
    });
  }
}

/* ------------------------------ Пользователи ------------------------------ */

const FIRST_NAMES = ['Азамат', 'Нурлан', 'Айгүл', 'Бакыт', 'Чолпон', 'Данияр', 'Элина', 'Тимур', 'Гүлназ', 'Марат', 'Асель', 'Ислам'];
const LAST_NAMES = ['Асанов', 'Токтогулов', 'Иманова', 'Сыдыков', 'Орозова', 'Жапаров', 'Бекова', 'Алиев', 'Касымова', 'Нурланов'];
const ROLE_POOL: Role[] = ['USER', 'USER', 'USER', 'USER', 'USER', 'USER', 'CONTRACTOR', 'CONTRACTOR', 'SPECIALIST', 'ADMIN'];

export const users: User[] = Array.from({ length: 48 }, (_, i) => {
  const first = pick(rng, FIRST_NAMES);
  const last = pick(rng, LAST_NAMES);
  const role = i === 0 ? 'ADMIN' : i === 1 ? 'SPECIALIST' : pick(rng, ROLE_POOL);
  const active = rng() > 0.12;
  return {
    id: i + 1,
    keycloakId: `kc-${1000 + i}`,
    email: `${translit(first).toLowerCase()}.${translit(last).toLowerCase()}${i}@mail.kg`,
    phone: `+996 ${500 + Math.floor(rng() * 99)} ${100000 + Math.floor(rng() * 899999)}`,
    firstName: first,
    lastName: last,
    role,
    emailVerified: rng() > 0.25,
    phoneVerified: rng() > 0.4,
    active,
    createdAt: ago(Math.floor(rng() * 500) * DAY),
    lastLoginAt: active ? ago(Math.floor(rng() * 20) * DAY + Math.floor(rng() * DAY)) : null,
    balance: Math.floor(rng() * 5000),
  };
});

/** Пул id обычных пользователей (для привязки броней/зарядок). */
const userKcIds = users.filter((u) => u.role === 'USER').map((u) => u.keycloakId!);
const userNameByKc = new Map(users.map((u) => [u.keycloakId!, `${u.firstName} ${u.lastName}`]));

/* ------------------------------ Транзакции ------------------------------ */

const TX_STATUSES: TransactionStatus[] = ['COMPLETED', 'COMPLETED', 'COMPLETED', 'COMPLETED', 'ACTIVE', 'CANCELLED', 'REJECTED'];

export const transactions: Transaction[] = Array.from({ length: 160 }, (_, i) => {
  const cb = pick(rng, chargeBoxes);
  const started = Date.now() - Math.floor(rng() * 45) * DAY - Math.floor(rng() * DAY);
  const status = pick(rng, TX_STATUSES);
  const durationMin = 15 + Math.floor(rng() * 90);
  const energy = Math.round((durationMin / 60) * (cb.kwCost ? Number(cb.power) : 40) * (0.5 + rng() * 0.5));
  const startValue = Math.floor(rng() * 50000);
  const active = status === 'ACTIVE';
  const kc = pick(rng, userKcIds);
  return {
    id: i + 1,
    transactionId: 100000 + i,
    chargeBoxId: cb.chargeBoxId,
    connectorId: 1 + Math.floor(rng() * (cb.connectorCount ?? 1)),
    startTimestamp: new Date(started).toISOString(),
    startValue,
    stopTimestamp: active ? null : new Date(started + durationMin * 60000).toISOString(),
    stopValue: active ? null : startValue + energy * 1000,
    transactionValue: active ? 0 : energy,
    status,
    reason: status === 'CANCELLED' ? 'Remote' : status === 'REJECTED' ? 'PaymentFailed' : null,
    userId: kc,
    userName: userNameByKc.get(kc),
    addressName: cb.addressName,
    createdAt: new Date(started).toISOString(),
    updatedAt: nowIso(),
    totalSum: status === 'COMPLETED' ? Math.round(energy * (cb.kwCost ?? 14)) : 0,
  };
});

/* ------------------------------ Брони ------------------------------ */

const bookableStations = chargeBoxes.filter((c) => (c.bookingMinuteCost ?? 0) > 0);

export const bookings: Booking[] = Array.from({ length: 70 }, (_, i) => {
  const cb = pick(rng, bookableStations.length ? bookableStations : chargeBoxes);
  const started = Date.now() - Math.floor(rng() * 40) * DAY - Math.floor(rng() * DAY);
  const minutes = 20 + Math.floor(rng() * 100);
  const active = rng() > 0.85;
  const status: Booking['status'] = active
    ? 'ACTIVE'
    : pick(rng, ['COMPLETED', 'COMPLETED', 'COMPLETED', 'CANCELLED', 'REJECTED']);
  const price = cb.bookingMinuteCost || 3;
  const kc = pick(rng, userKcIds);
  return {
    id: i + 1,
    bookingId: `bk-${crypto.randomUUID().slice(0, 8)}`,
    userId: kc,
    userName: userNameByKc.get(kc),
    stationId: cb.chargeBoxId,
    addressName: cb.addressName,
    connectorId: 1 + Math.floor(rng() * (cb.connectorCount ?? 1)),
    pricePerMinute: price,
    totalMinutes: minutes,
    totalSum: status === 'COMPLETED' ? minutes * price : active ? 0 : Math.round(minutes * price * 0.4),
    startedAt: new Date(started).toISOString(),
    endedAt: active ? null : new Date(started + minutes * 60000).toISOString(),
    status,
    createdAt: new Date(started).toISOString(),
  };
});

/** Быстрый доступ ownerId по chargeBoxId — для скоупинга контрагента. */
export const ownerByStation = new Map(chargeBoxes.map((c) => [c.chargeBoxId, c.ownerId]));

/* ------------------------------ helpers ------------------------------ */

function translit(s: string): string {
  const map: Record<string, string> = {
    а: 'a', б: 'b', в: 'v', г: 'g', д: 'd', е: 'e', ж: 'zh', з: 'z', и: 'i', й: 'y',
    к: 'k', л: 'l', м: 'm', н: 'n', о: 'o', п: 'p', р: 'r', с: 's', т: 't', у: 'u',
    ф: 'f', х: 'h', ц: 'c', ч: 'ch', ш: 'sh', щ: 'sch', ъ: '', ы: 'y', ь: '', э: 'e',
    ю: 'yu', я: 'ya', ү: 'u', ө: 'o', ң: 'ng', і: 'i',
  };
  return s
    .toLowerCase()
    .split('')
    .map((ch) => map[ch] ?? ch)
    .join('');
}
