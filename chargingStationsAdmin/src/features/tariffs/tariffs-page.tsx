import { useEffect, useState } from 'react';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { Coins, Info, Loader2, RotateCcw, Save } from 'lucide-react';
import { PageHeader } from '@/components/page-header';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { ChartTooltip } from '@/components/chart-tooltip';
import { useHourlyTariffs, useSaveHourlyTariffs, useStations } from '@/api/hooks';
import { toast } from '@/store/toast';
import { formatSom } from '@/lib/format';
import type { ChargeBox, HourTariff } from '@/types/domain';

/** Дефолтная кривая суток от базового тарифа: дневной и вечерний пик дороже, ночь дешевле. */
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

/** 24 часа из ответа сервера (недостающие часы добираем базовым тарифом). */
function normalize(loaded: HourTariff[], station?: ChargeBox): HourTariff[] {
  const baseKw = station?.kwCost ?? 14;
  const baseBooking = station?.bookingMinuteCost ?? 3;
  if (!loaded.length) return defaultHours(baseKw, baseBooking);
  const map = new Map(loaded.map((t) => [t.hour, t]));
  return Array.from({ length: 24 }, (_, h) => map.get(h) ?? { hour: h, kwCost: baseKw, bookingMinuteCost: baseBooking });
}

export function TariffsPage() {
  const stations = useStations();
  const [stationId, setStationId] = useState('');
  const tariffs = useHourlyTariffs(stationId || undefined);
  const save = useSaveHourlyTariffs();

  const [hours, setHours] = useState<HourTariff[]>([]);
  const [dirty, setDirty] = useState(false);

  const station = stations.data?.find((s) => s.chargeBoxId === stationId);

  // Выбираем первую станцию по умолчанию.
  useEffect(() => {
    if (!stationId && stations.data?.length) setStationId(stations.data[0].chargeBoxId);
  }, [stations.data, stationId]);

  // Загружаем расписание с сервера (или строим дефолт), когда сменилась станция/пришли данные.
  useEffect(() => {
    if (!stationId || tariffs.isLoading) return;
    const st = stations.data?.find((s) => s.chargeBoxId === stationId);
    setHours(normalize(tariffs.data ?? [], st));
    setDirty(false);
  }, [stationId, tariffs.data, tariffs.isLoading, stations.data]);

  const updateHour = (h: number, field: keyof HourTariff, value: number) => {
    setHours((prev) => prev.map((x) => (x.hour === h ? { ...x, [field]: value } : x)));
    setDirty(true);
  };

  const reset = () => {
    setHours(normalize(tariffs.data ?? [], station));
    setDirty(false);
  };

  const onSave = () => {
    if (!stationId) return;
    save.mutate(
      { stationId, tariffs: hours },
      {
        onSuccess: () => {
          toast.success('Тарифы сохранены', `${station?.addressName ?? stationId} · 24 ч`);
          setDirty(false);
        },
        onError: (e) => toast.error('Не удалось сохранить', e.message),
      },
    );
  };

  const chartData = hours.map((h) => ({ hour: `${String(h.hour).padStart(2, '0')}:00`, kwCost: h.kwCost, booking: h.bookingMinuteCost }));
  const avg = hours.length ? hours.reduce((s, h) => s + h.kwCost, 0) / hours.length : 0;
  const configured = (tariffs.data?.length ?? 0) > 0;

  return (
    <div className="space-y-6">
      <PageHeader
        title="Тарифы"
        description="Почасовые тарифы зарядки (сом/кВт·ч) и брони (сом/мин). Планировщик применяет тариф текущего часа к станции."
        actions={
          <>
            <Button variant="outline" onClick={reset} disabled={!dirty || save.isPending}>
              <RotateCcw className="size-4" />
              Сбросить
            </Button>
            <Button variant="accent" onClick={onSave} disabled={!dirty || save.isPending || !stationId}>
              {save.isPending ? <Loader2 className="size-4 animate-spin" /> : <Save className="size-4" />}
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
                <div className="text-xs text-muted-foreground">Текущий кВт·ч</div>
                <div className="mt-1 text-lg font-bold">{formatSom(station?.kwCost ?? 0)}</div>
              </div>
              <div className="rounded-lg bg-muted/50 p-3">
                <div className="text-xs text-muted-foreground">Средний за сутки</div>
                <div className="mt-1 text-lg font-bold">{formatSom(avg)}</div>
              </div>
            </div>
            <div className="flex items-start gap-2 rounded-lg border border-border p-3 text-xs text-muted-foreground">
              <Info className="mt-0.5 size-4 shrink-0 text-info" />
              {configured
                ? 'Расписание задано. Планировщик обновляет активный тариф станции каждый час.'
                : 'Расписание ещё не задано — показана авто-кривая от базового тарифа. Сохраните, чтобы применить.'}
            </div>
          </CardContent>
        </Card>

        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>Цена по часам</CardTitle>
          </CardHeader>
          <CardContent>
            <ResponsiveContainer width="100%" height={220}>
              <BarChart data={chartData} margin={{ left: -14, right: 8, top: 4 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" vertical={false} />
                <XAxis dataKey="hour" tick={{ fontSize: 10, fill: 'var(--color-muted-foreground)' }} axisLine={false} tickLine={false} interval={2} />
                <YAxis tick={{ fontSize: 11, fill: 'var(--color-muted-foreground)' }} axisLine={false} tickLine={false} width={40} />
                <Tooltip cursor={{ fill: 'var(--color-muted)', opacity: 0.4 }} content={<ChartTooltip formatter={(v) => formatSom(v)} />} />
                <Bar dataKey="kwCost" name="кВт·ч" fill="var(--color-accent)" radius={[3, 3, 0, 0]} maxBarSize={20} />
                <Bar dataKey="booking" name="бронь/мин" fill="var(--color-primary)" radius={[3, 3, 0, 0]} maxBarSize={20} />
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
          {tariffs.isLoading ? (
            <p className="py-8 text-center text-sm text-muted-foreground">Загрузка расписания…</p>
          ) : (
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
          )}
        </CardContent>
      </Card>
    </div>
  );
}
