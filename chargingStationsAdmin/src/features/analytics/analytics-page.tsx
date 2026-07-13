import { useMemo, useState } from 'react';
import {
  Area,
  AreaChart,
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { BatteryCharging, CalendarClock, Clock, Download, Users, Wallet, Zap } from 'lucide-react';
import { PageHeader } from '@/components/page-header';
import { StatCard } from '@/components/stat-card';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Segmented } from '@/components/ui/segmented';
import { Select } from '@/components/ui/select';
import { ChartTooltip } from '@/components/chart-tooltip';
import { useBookingAnalytics, useEnergyAnalytics, useRevenueAnalytics, useStations } from '@/api/hooks';
import { useAuth } from '@/store/auth';
import { downloadCsv } from '@/lib/csv';
import { formatDuration, formatKwh, formatNumber, formatSom, formatSomCompact } from '@/lib/format';
import type { Granularity, GroupBy } from '@/types/domain';

type Metric = 'revenue' | 'energy' | 'bookings';
type Preset = '7' | '30' | '90' | '365';

const PALETTE = [
  'var(--color-primary)',
  'var(--color-accent)',
  'var(--color-info)',
  'var(--color-success)',
  'oklch(0.7 0.16 330)',
  'oklch(0.68 0.15 200)',
];

const PRESET_GRAN: Record<Preset, Granularity> = { '7': 'DAY', '30': 'DAY', '90': 'WEEK', '365': 'MONTH' };

export function AnalyticsPage() {
  const role = useAuth((s) => s.account?.role ?? 'USER');
  const [metric, setMetric] = useState<Metric>('revenue');
  const [preset, setPreset] = useState<Preset>('30');
  const [granularity, setGranularity] = useState<Granularity>('DAY');
  const [groupBy, setGroupBy] = useState<GroupBy>('TOTAL');
  const [stationId, setStationId] = useState('');
  const stations = useStations();

  const opts = useMemo(() => {
    const to = new Date();
    to.setMinutes(0, 0, 0);
    const from = new Date(to.getTime() - Number(preset) * 86_400_000);
    return {
      from: from.toISOString(),
      to: to.toISOString(),
      granularity,
      groupBy,
      stationIds: stationId ? [stationId] : undefined,
    };
  }, [preset, granularity, groupBy, stationId]);

  const revenue = useRevenueAnalytics(opts);
  const energy = useEnergyAnalytics(opts);
  const bookings = useBookingAnalytics(opts);

  const loading = revenue.isLoading || energy.isLoading || bookings.isLoading;

  const tickFmt = (iso: string) =>
    new Date(iso).toLocaleDateString('ru-RU',
      granularity === 'MONTH' || granularity === 'YEAR'
        ? { month: 'short', year: '2-digit' }
        : { day: '2-digit', month: 'short' });

  // Слияние серий в строки графика по периодам.
  const chartRows = useMemo(() => {
    const src =
      metric === 'revenue' ? revenue.data : metric === 'energy' ? energy.data : bookings.data;
    if (!src) return [];
    const value = (p: unknown): number => {
      const point = p as Record<string, number>;
      if (metric === 'revenue') return point.totalRevenue;
      if (metric === 'energy') return point.energyKwh;
      return point.bookings;
    };
    const map = new Map<string, Record<string, number | string>>();
    for (const s of src.series)
      for (const p of s.points) {
        const row = map.get(p.periodStart) ?? { periodStart: p.periodStart };
        row[s.label] = value(p);
        // для стека выручки (TOTAL)
        if (metric === 'revenue' && groupBy === 'TOTAL') {
          const rp = p as { chargingRevenue: number; bookingRevenue: number };
          row['Зарядки'] = rp.chargingRevenue;
          row['Брони'] = rp.bookingRevenue;
        }
        map.set(p.periodStart, row);
      }
    return [...map.values()].sort(
      (a, b) => +new Date(a.periodStart as string) - +new Date(b.periodStart as string),
    );
  }, [metric, groupBy, revenue.data, energy.data, bookings.data]);

  const seriesLabels =
    (metric === 'revenue' ? revenue.data : metric === 'energy' ? energy.data : bookings.data)?.series.map(
      (s) => s.label,
    ) ?? [];

  const fmtValue = (v: number) =>
    metric === 'revenue' ? formatSom(v) : metric === 'energy' ? formatKwh(v) : formatNumber(v);

  const stackedRevenue = metric === 'revenue' && groupBy === 'TOTAL';

  const onExport = () => {
    const headers = ['Период', ...(stackedRevenue ? ['Зарядки', 'Брони'] : seriesLabels)];
    const rows = chartRows.map((r) => [
      tickFmt(r.periodStart as string),
      ...(stackedRevenue
        ? [Number(r['Зарядки'] ?? 0), Number(r['Брони'] ?? 0)]
        : seriesLabels.map((l) => Number(r[l] ?? 0))),
    ]);
    downloadCsv(`batenergy-${metric}-${preset}d.csv`, headers, rows);
  };

  return (
    <div className="space-y-6">
      <PageHeader title="Аналитика" description="Выручка, потребление энергии и бронирования" />

      {/* Управление */}
      <Card className="p-4">
        <div className="flex flex-wrap items-center gap-3">
          <Segmented
            value={metric}
            onChange={setMetric}
            options={[
              { value: 'revenue', label: 'Выручка' },
              { value: 'energy', label: 'Энергия' },
              { value: 'bookings', label: 'Брони' },
            ]}
          />
          <div className="ml-auto flex flex-wrap items-center gap-2">
            <Segmented
              size="sm"
              value={preset}
              onChange={(p) => {
                setPreset(p);
                setGranularity(PRESET_GRAN[p]);
              }}
              options={[
                { value: '7', label: '7 дней' },
                { value: '30', label: '30 дней' },
                { value: '90', label: '90 дней' },
                { value: '365', label: '12 мес' },
              ]}
            />
            <Select value={granularity} onChange={(e) => setGranularity(e.target.value as Granularity)} className="w-32">
              <option value="HOUR">По часам</option>
              <option value="DAY">По дням</option>
              <option value="WEEK">По неделям</option>
              <option value="MONTH">По месяцам</option>
            </Select>
            <Select value={groupBy} onChange={(e) => setGroupBy(e.target.value as GroupBy)} className="w-40">
              <option value="TOTAL">Всего</option>
              <option value="STATION">По станциям</option>
              {role !== 'CONTRACTOR' && <option value="OWNER">По владельцам</option>}
            </Select>
            <Select value={stationId} onChange={(e) => setStationId(e.target.value)} className="w-48">
              <option value="">Все станции</option>
              {stations.data?.map((s) => (
                <option key={s.chargeBoxId} value={s.chargeBoxId}>
                  {s.addressName ?? s.chargeBoxId}
                </option>
              ))}
            </Select>
            <Button variant="outline" size="sm" onClick={onExport} title="Экспорт в CSV">
              <Download className="size-4" />
              CSV
            </Button>
          </div>
        </div>
      </Card>

      {/* Сводка */}
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {metric === 'revenue' && (
          <>
            <StatCard label="Общая выручка" value={formatSomCompact(revenue.data?.summary.totalRevenue ?? 0)} icon={Wallet} tone="accent" loading={loading} />
            <StatCard label="Выручка с зарядок" value={formatSomCompact(revenue.data?.summary.chargingRevenue ?? 0)} icon={BatteryCharging} tone="primary" loading={loading} />
            <StatCard label="Выручка с броней" value={formatSomCompact(revenue.data?.summary.bookingRevenue ?? 0)} icon={CalendarClock} tone="info" loading={loading} />
            <StatCard label="Зарядных сессий" value={formatNumber(revenue.data?.summary.chargingSessions ?? 0)} icon={Zap} tone="success" loading={loading} />
          </>
        )}
        {metric === 'energy' && (
          <>
            <StatCard label="Всего энергии" value={formatKwh(energy.data?.summary.totalEnergyKwh ?? 0)} icon={BatteryCharging} tone="success" loading={loading} />
            <StatCard label="Сессий" value={formatNumber(energy.data?.summary.totalSessions ?? 0)} icon={Zap} tone="primary" loading={loading} />
            <StatCard label="Средне за сессию" value={formatKwh(energy.data?.summary.avgEnergyPerSessionKwh ?? 0)} icon={Wallet} tone="accent" loading={loading} />
            <StatCard label="Уник. пользователей" value={formatNumber(energy.data?.summary.uniqueUsers ?? 0)} icon={Users} tone="info" loading={loading} />
          </>
        )}
        {metric === 'bookings' && (
          <>
            <StatCard label="Всего броней" value={formatNumber(bookings.data?.summary.totalBookings ?? 0)} icon={CalendarClock} tone="primary" loading={loading} />
            <StatCard label="Всего минут" value={formatNumber(bookings.data?.summary.totalMinutes ?? 0)} icon={Clock} tone="info" loading={loading} />
            <StatCard label="Средняя длительность" value={formatDuration(bookings.data?.summary.avgDurationMinutes ?? 0)} icon={Clock} tone="accent" loading={loading} />
            <StatCard label="Выручка с броней" value={formatSomCompact(bookings.data?.summary.totalRevenue ?? 0)} icon={Wallet} tone="success" loading={loading} />
          </>
        )}
      </div>

      {/* График */}
      <Card>
        <CardHeader>
          <CardTitle>
            {metric === 'revenue' ? 'Динамика выручки' : metric === 'energy' ? 'Динамика энергии' : 'Динамика броней'}
          </CardTitle>
        </CardHeader>
        <CardContent>
          <ResponsiveContainer width="100%" height={360}>
            {stackedRevenue ? (
              <AreaChart data={chartRows} margin={{ left: -8, right: 8, top: 4 }}>
                <defs>
                  <linearGradient id="aCharge" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="var(--color-primary)" stopOpacity={0.5} />
                    <stop offset="100%" stopColor="var(--color-primary)" stopOpacity={0} />
                  </linearGradient>
                  <linearGradient id="aBook" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="var(--color-accent)" stopOpacity={0.5} />
                    <stop offset="100%" stopColor="var(--color-accent)" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" vertical={false} />
                <XAxis dataKey="periodStart" tickFormatter={tickFmt} tick={{ fontSize: 11, fill: 'var(--color-muted-foreground)' }} axisLine={false} tickLine={false} minTickGap={28} />
                <YAxis tick={{ fontSize: 11, fill: 'var(--color-muted-foreground)' }} axisLine={false} tickLine={false} width={52} />
                <Tooltip content={<ChartTooltip labelFormatter={tickFmt} formatter={(v) => formatSom(v)} />} />
                <Legend wrapperStyle={{ fontSize: 12 }} />
                <Area type="monotone" dataKey="Зарядки" stackId="1" stroke="var(--color-primary)" strokeWidth={2} fill="url(#aCharge)" />
                <Area type="monotone" dataKey="Брони" stackId="1" stroke="var(--color-accent)" strokeWidth={2} fill="url(#aBook)" />
              </AreaChart>
            ) : (
              <LineChart data={chartRows} margin={{ left: -8, right: 8, top: 4 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" vertical={false} />
                <XAxis dataKey="periodStart" tickFormatter={tickFmt} tick={{ fontSize: 11, fill: 'var(--color-muted-foreground)' }} axisLine={false} tickLine={false} minTickGap={28} />
                <YAxis tick={{ fontSize: 11, fill: 'var(--color-muted-foreground)' }} axisLine={false} tickLine={false} width={52} />
                <Tooltip content={<ChartTooltip labelFormatter={tickFmt} formatter={fmtValue} />} />
                {seriesLabels.length > 1 && <Legend wrapperStyle={{ fontSize: 12 }} />}
                {seriesLabels.map((label, i) => (
                  <Line
                    key={label}
                    type="monotone"
                    dataKey={label}
                    stroke={PALETTE[i % PALETTE.length]}
                    strokeWidth={2}
                    dot={false}
                    activeDot={{ r: 4 }}
                  />
                ))}
              </LineChart>
            )}
          </ResponsiveContainer>
        </CardContent>
      </Card>
    </div>
  );
}
