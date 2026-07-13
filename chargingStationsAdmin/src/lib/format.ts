/** Форматтеры отображения. Валюта платформы — сом (KGS). */

const numberFmt = new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 0 });
const decimalFmt = new Intl.NumberFormat('ru-RU', {
  minimumFractionDigits: 0,
  maximumFractionDigits: 1,
});

export function formatNumber(value: number): string {
  return numberFmt.format(value);
}

/** Сумма в сомах: «12 500 сом». */
export function formatSom(value: number, withCurrency = true): string {
  const n = decimalFmt.format(Math.round(value));
  return withCurrency ? `${n} сом` : n;
}

/** Компактная сумма: «1.2 млн», «12.5 тыс». */
export function formatSomCompact(value: number): string {
  if (value >= 1_000_000) return `${decimalFmt.format(value / 1_000_000)} млн сом`;
  if (value >= 10_000) return `${decimalFmt.format(value / 1000)} тыс сом`;
  return formatSom(value);
}

export function formatKwh(value: number): string {
  return `${decimalFmt.format(value)} кВт·ч`;
}

export function formatKw(value: number | string | null | undefined): string {
  if (value == null || value === '') return '—';
  const n = typeof value === 'string' ? parseFloat(value) : value;
  if (Number.isNaN(n)) return String(value);
  return `${decimalFmt.format(n)} кВт`;
}

export function formatPercent(value: number, digits = 1): string {
  return `${value >= 0 ? '+' : ''}${value.toFixed(digits)}%`;
}

const dateTimeFmt = new Intl.DateTimeFormat('ru-RU', {
  day: '2-digit',
  month: 'short',
  hour: '2-digit',
  minute: '2-digit',
});
const dateFmt = new Intl.DateTimeFormat('ru-RU', {
  day: '2-digit',
  month: 'short',
  year: 'numeric',
});

export function formatDateTime(iso: string | Date | null | undefined): string {
  if (!iso) return '—';
  return dateTimeFmt.format(new Date(iso));
}

export function formatDate(iso: string | Date | null | undefined): string {
  if (!iso) return '—';
  return dateFmt.format(new Date(iso));
}

/** Относительное время: «5 мин назад», «2 ч назад». */
export function formatRelative(iso: string | Date | null | undefined): string {
  if (!iso) return '—';
  const diff = Date.now() - new Date(iso).getTime();
  const min = Math.floor(diff / 60000);
  if (min < 1) return 'только что';
  if (min < 60) return `${min} мин назад`;
  const h = Math.floor(min / 60);
  if (h < 24) return `${h} ч назад`;
  const d = Math.floor(h / 24);
  if (d < 30) return `${d} дн назад`;
  return formatDate(iso);
}

/** Длительность в минутах → «1 ч 20 мин». */
export function formatDuration(minutes: number | null | undefined): string {
  if (minutes == null) return '—';
  const m = Math.round(minutes);
  if (m < 60) return `${m} мин`;
  const h = Math.floor(m / 60);
  const rem = m % 60;
  return rem ? `${h} ч ${rem} мин` : `${h} ч`;
}

/** Инициалы для аватара. */
export function initials(first?: string | null, last?: string | null): string {
  const a = (first ?? '').trim()[0] ?? '';
  const b = (last ?? '').trim()[0] ?? '';
  return (a + b).toUpperCase() || '—';
}
