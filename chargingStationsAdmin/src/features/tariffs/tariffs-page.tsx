import { useEffect, useMemo, useState } from 'react';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { Coins, RotateCcw, Save } from 'lucide-react';
import { PageHeader } from '@/components/page-header';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { ChartTooltip } from '@/components/chart-tooltip';
import { useStations } from '@/api/hooks';
import { formatSom } from '@/lib/format';
import type { HourTariff } from '@/types/domain';

/** Кривая суток: дневной и вечерний пик дороже. */
function defaultHours(baseKw: number, baseBooking: number): HourTariff[] {
  return Array.from({ length: 24 }, (_, h) => {
    const peak = (h >= 8 && h <= 11) || (h >= 18 && h <= 22) ? 1.25 : h >= 0 && h <= 6 ? 0.8 : 1;
    return {
      hour: h,
      kwCost: Math.round(baseKw * peak * 10) / 10,
      bookingMinuteCost: Math.round(baseBooking * peak * 10) / 10,
    };
  });
}

export function TariffsPage() {
  const stations = useStations();
  const [stationId, setStationId] = useState<string>('');
  const [hours, setHours] = useState<HourTariff[]>([]);
  const [dirty, setDirty] = useState(false);
  const [saved, setSaved] = useState(false);

  const station = useMemo(
    () => stations.data?.find((s) => s.chargeBoxId === stationId),
    [stations.data, stationId],
  );

  useEffect(() => {
    if (!stationId && stations.data?.length) setStationId(stations.data[0].chargeBoxId);
  }, [stations.data, stationId]);

  useEffect(() => {
    if (station) {
      setHours(defaultHours(station.kwCost ?? 14, station.bookingMinuteCost ?? 3));
      setDirty(false);
    }
  }, [station]);

  const updateHour = (h: number, field: keyof HourTariff, value: number) => {
    setHours((prev) => prev.map((x) => (x.hour === h ? { ...x, [field]: value } : x)));
    setDirty(true);
    setSaved(false);
  };

  const reset = () => {
    if (station) setHours(defaultHours(station.kwCost ?? 14, station.bookingMinuteCost ?? 3));
    setDirty(false);
  };

  const save = () => {
    // Фаза 2: PUT /contractor-admin/api/stations/{id}/hourly-tariffs
    setDirty(false);
    setSaved(true);
  };

  const chartData = hours.map((h) => ({ hour: `${String(h.hour).padStart(2, '0')}:00`, kwCost: h.kwCost }));
  const avg = hours.length ? hours.reduce((s, h) => s + h.kwCost, 0) / hours.length : 0;

  return (
    <div className="space-y-6">
      <PageHeader
        title="Тарифы"
        description="Почасовые тарифы зарядки и бронирования для станции"
        actions={
          <>
            <Button variant="outline" onClick={reset} disabled={!dirty}>
              <RotateCcw className="size-4" />
              Сбросить
            </Button>
            <Button variant="accent" onClick={save} disabled={!dirty}>
              <Save className="size-4" />
              Сохранить
            </Button>
          </>
        }
      />

      <div className="grid gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-1">
          <CardHeader>
            <CardTitle>Станция</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <Select value={stationId} onChange={(e) => setStationId(e.target.value)}>
              {stations.data?.map((s) => (
                <option key={s.chargeBoxId} value={s.chargeBoxId}>
                  {s.addressName ?? s.chargeBoxId} · {s.chargeBoxId}
                </option>
              ))}
            </Select>
            <div className="grid grid-cols-2 gap-3">
              <div className="rounded-lg bg-muted/50 p-3">
                <div className="text-xs text-muted-foreground">Базовый кВт·ч</div>
                <div className="mt-1 text-lg font-bold">{formatSom(station?.kwCost ?? 0)}</div>
              </div>
              <div className="rounded-lg bg-muted/50 p-3">
                <div className="text-xs text-muted-foreground">Средний за сутки</div>
                <div className="mt-1 text-lg font-bold">{formatSom(avg)}</div>
              </div>
            </div>
            {saved && (
              <div className="rounded-md bg-success/10 px-3 py-2 text-sm text-success">
                Тарифы сохранены (демо). В Фазе 2 — запрос к API.
              </div>
            )}
          </CardContent>
        </Card>

        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>Цена зарядки по часам</CardTitle>
          </CardHeader>
          <CardContent>
            <ResponsiveContainer width="100%" height={220}>
              <BarChart data={chartData} margin={{ left: -14, right: 8, top: 4 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" vertical={false} />
                <XAxis dataKey="hour" tick={{ fontSize: 10, fill: 'var(--color-muted-foreground)' }} axisLine={false} tickLine={false} interval={2} />
                <YAxis tick={{ fontSize: 11, fill: 'var(--color-muted-foreground)' }} axisLine={false} tickLine={false} width={40} />
                <Tooltip cursor={{ fill: 'var(--color-muted)', opacity: 0.4 }} content={<ChartTooltip formatter={(v) => formatSom(v)} />} />
                <Bar dataKey="kwCost" name="кВт·ч" fill="var(--color-accent)" radius={[3, 3, 0, 0]} maxBarSize={20} />
              </BarChart>
            </ResponsiveContainer>
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Редактор по часам</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-6">
            {hours.map((h) => (
              <div key={h.hour} className="rounded-lg border border-border p-3">
                <div className="mb-2 flex items-center gap-1.5 text-sm font-semibold">
                  <Coins className="size-3.5 text-accent" />
                  {String(h.hour).padStart(2, '0')}:00
                </div>
                <label className="text-[11px] text-muted-foreground">кВт·ч, сом</label>
                <Input
                  type="number"
                  value={h.kwCost}
                  min={0}
                  step={0.5}
                  className="mt-0.5 h-8"
                  onChange={(e) => updateHour(h.hour, 'kwCost', Number(e.target.value))}
                />
                <label className="mt-2 block text-[11px] text-muted-foreground">бронь/мин, сом</label>
                <Input
                  type="number"
                  value={h.bookingMinuteCost}
                  min={0}
                  step={0.5}
                  className="mt-0.5 h-8"
                  onChange={(e) => updateHour(h.hour, 'bookingMinuteCost', Number(e.target.value))}
                />
              </div>
            ))}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
