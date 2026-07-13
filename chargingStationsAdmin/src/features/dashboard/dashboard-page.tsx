import { useMemo } from 'react';
import { Link } from '@tanstack/react-router';
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { BatteryCharging, Coins, Wallet, Zap } from 'lucide-react';
import { PageHeader } from '@/components/page-header';
import { StatCard } from '@/components/stat-card';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { ChartTooltip } from '@/components/chart-tooltip';
import { TransactionStatusBadge, connectorStatusColor } from '@/components/status';
import { Avatar } from '@/components/ui/avatar';
import { useBookings, useConnectors, useEnergyAnalytics, useRevenueAnalytics, useStations, useTransactions } from '@/api/hooks';
import { formatKwh, formatRelative, formatSom, formatSomCompact } from '@/lib/format';
import type { ConnectorStatus } from '@/types/domain';

const analyticsOpts = () => {
  const to = new Date();
  to.setMinutes(0, 0, 0);
  const from = new Date(to.getTime() - 29 * 86_400_000);
  return { from: from.toISOString(), to: to.toISOString(), granularity: 'DAY' as const, groupBy: 'TOTAL' as const };
};

const tick = (iso: string) =>
  new Date(iso).toLocaleDateString('ru-RU', { day: '2-digit', month: 'short' });

export function DashboardPage() {
  const opts = useMemo(analyticsOpts, []);
  const stations = useStations();
  const connectors = useConnectors();
  const transactions = useTransactions();
  const bookings = useBookings();
  const revenue = useRevenueAnalytics(opts);
  const energy = useEnergyAnalytics(opts);

  const onlineCount = stations.data?.filter((s) => s.online).length ?? 0;
  const activeSessions = transactions.data?.filter((t) => t.status === 'ACTIVE').length ?? 0;
  const activeBookings = bookings.data?.filter((b) => b.status === 'ACTIVE').length ?? 0;

  const revenuePoints = revenue.data?.series[0]?.points ?? [];
  const energyPoints = energy.data?.series[0]?.points ?? [];

  const connectorDist = useMemo(() => {
    const counts = new Map<ConnectorStatus, number>();
    for (const c of connectors.data ?? []) counts.set(c.status, (counts.get(c.status) ?? 0) + 1);
    return [...counts.entries()].map(([status, value]) => ({ status, value }));
  }, [connectors.data]);

  const recent = (transactions.data ?? []).slice(0, 6);

  return (
    <div className="space-y-6">
      <PageHeader
        title="Дашборд"
        description="Ключевые показатели сети за последние 30 дней"
      />

      {/* KPI */}
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard
          label="Станции онлайн"
          value={`${onlineCount} / ${stations.data?.length ?? 0}`}
          icon={Zap}
          hint="активны сейчас"
          tone="primary"
          loading={stations.isLoading}
        />
        <StatCard
          label="Выручка за 30 дней"
          value={formatSomCompact(revenue.data?.summary.totalRevenue ?? 0)}
          icon={Wallet}
          delta={12.4}
          tone="accent"
          loading={revenue.isLoading}
        />
        <StatCard
          label="Энергия за 30 дней"
          value={formatKwh(energy.data?.summary.totalEnergyKwh ?? 0)}
          icon={BatteryCharging}
          delta={8.1}
          tone="success"
          loading={energy.isLoading}
        />
        <StatCard
          label="Активные сессии"
          value={`${activeSessions}`}
          icon={Coins}
          hint={`${activeBookings} активных броней`}
          tone="info"
          loading={transactions.isLoading}
        />
      </div>

      {/* Выручка + распределение коннекторов */}
      <div className="grid gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader className="flex-row items-center justify-between">
            <div>
              <CardTitle>Выручка</CardTitle>
              <CardDescription>Зарядки и брони, посуточно</CardDescription>
            </div>
          </CardHeader>
          <CardContent>
            <ResponsiveContainer width="100%" height={280}>
              <AreaChart data={revenuePoints} margin={{ left: -12, right: 8, top: 4 }}>
                <defs>
                  <linearGradient id="gCharge" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="var(--color-primary)" stopOpacity={0.5} />
                    <stop offset="100%" stopColor="var(--color-primary)" stopOpacity={0} />
                  </linearGradient>
                  <linearGradient id="gBook" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="var(--color-accent)" stopOpacity={0.5} />
                    <stop offset="100%" stopColor="var(--color-accent)" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" vertical={false} />
                <XAxis
                  dataKey="periodStart"
                  tickFormatter={tick}
                  tick={{ fontSize: 11, fill: 'var(--color-muted-foreground)' }}
                  axisLine={false}
                  tickLine={false}
                  minTickGap={28}
                />
                <YAxis
                  tick={{ fontSize: 11, fill: 'var(--color-muted-foreground)' }}
                  axisLine={false}
                  tickLine={false}
                  width={48}
                />
                <Tooltip content={<ChartTooltip labelFormatter={tick} formatter={(v) => formatSom(v)} />} />
                <Area
                  type="monotone"
                  name="Зарядки"
                  dataKey="chargingRevenue"
                  stroke="var(--color-primary)"
                  strokeWidth={2}
                  fill="url(#gCharge)"
                  stackId="1"
                />
                <Area
                  type="monotone"
                  name="Брони"
                  dataKey="bookingRevenue"
                  stroke="var(--color-accent)"
                  strokeWidth={2}
                  fill="url(#gBook)"
                  stackId="1"
                />
              </AreaChart>
            </ResponsiveContainer>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Коннекторы</CardTitle>
            <CardDescription>Распределение по статусам</CardDescription>
          </CardHeader>
          <CardContent>
            <ResponsiveContainer width="100%" height={180}>
              <PieChart>
                <Pie
                  data={connectorDist}
                  dataKey="value"
                  nameKey="status"
                  innerRadius={52}
                  outerRadius={80}
                  paddingAngle={2}
                  strokeWidth={0}
                >
                  {connectorDist.map((d) => (
                    <Cell key={d.status} fill={connectorStatusColor[d.status]} />
                  ))}
                </Pie>
                <Tooltip content={<ChartTooltip />} />
              </PieChart>
            </ResponsiveContainer>
            <div className="mt-3 grid grid-cols-2 gap-2 text-xs">
              {connectorDist.map((d) => (
                <div key={d.status} className="flex items-center gap-1.5">
                  <span
                    className="size-2.5 rounded-full"
                    style={{ background: connectorStatusColor[d.status] }}
                  />
                  <span className="text-muted-foreground">{d.status}</span>
                  <span className="ml-auto font-semibold">{d.value}</span>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Энергия + недавние зарядки */}
      <div className="grid gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>Потребление энергии</CardTitle>
            <CardDescription>кВт·ч по дням</CardDescription>
          </CardHeader>
          <CardContent>
            <ResponsiveContainer width="100%" height={240}>
              <BarChart data={energyPoints} margin={{ left: -12, right: 8, top: 4 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" vertical={false} />
                <XAxis
                  dataKey="periodStart"
                  tickFormatter={tick}
                  tick={{ fontSize: 11, fill: 'var(--color-muted-foreground)' }}
                  axisLine={false}
                  tickLine={false}
                  minTickGap={28}
                />
                <YAxis
                  tick={{ fontSize: 11, fill: 'var(--color-muted-foreground)' }}
                  axisLine={false}
                  tickLine={false}
                  width={48}
                />
                <Tooltip
                  cursor={{ fill: 'var(--color-muted)', opacity: 0.4 }}
                  content={<ChartTooltip labelFormatter={tick} formatter={(v) => formatKwh(v)} />}
                />
                <Bar name="Энергия" dataKey="energyKwh" fill="var(--color-primary)" radius={[4, 4, 0, 0]} maxBarSize={26} />
              </BarChart>
            </ResponsiveContainer>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex-row items-center justify-between">
            <CardTitle>Недавние зарядки</CardTitle>
            <Link to="/transactions" className="text-xs font-semibold text-primary hover:underline">
              все →
            </Link>
          </CardHeader>
          <CardContent className="space-y-1">
            {recent.map((t) => (
              <div key={t.id} className="flex items-center gap-3 rounded-lg px-1 py-2">
                <Avatar name={t.userName ?? t.userId} size={34} />
                <div className="min-w-0 flex-1">
                  <div className="truncate text-sm font-medium">{t.userName ?? 'Пользователь'}</div>
                  <div className="truncate text-xs text-muted-foreground">
                    {t.chargeBoxId} · {formatRelative(t.startTimestamp)}
                  </div>
                </div>
                <div className="text-right">
                  <div className="text-sm font-semibold">{formatKwh(t.transactionValue)}</div>
                  <TransactionStatusBadge status={t.status} />
                </div>
              </div>
            ))}
            {recent.length === 0 && (
              <p className="py-8 text-center text-sm text-muted-foreground">Пока нет зарядных сессий</p>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
