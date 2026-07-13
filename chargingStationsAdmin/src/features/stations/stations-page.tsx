import { useMemo, useState } from 'react';
import { Eye, MapPin, MoreVertical, Pencil, Plus, Power, Search, Trash2, Wrench } from 'lucide-react';
import { PageHeader } from '@/components/page-header';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Segmented } from '@/components/ui/segmented';
import { Dropdown, DropdownItem, DropdownLabel, DropdownSeparator } from '@/components/ui/dropdown';
import { ConfirmDialog } from '@/components/ui/confirm-dialog';
import { DataTable, type Column } from '@/components/data-table';
import { EmptyState } from '@/components/empty-state';
import { OnlineDot, ServiceStatusBadge, connectorStatusColor } from '@/components/status';
import { StationForm } from './station-form';
import { StationDetailDrawer } from './station-detail-drawer';
import { useConnectors, useDeleteStation, useSetServiceStatus, useStations } from '@/api/hooks';
import { USE_MOCK } from '@/api/client';
import { useAuth } from '@/store/auth';
import { toast } from '@/store/toast';
import { formatKw, formatSom } from '@/lib/format';
import type { ChargeBox, Connector, ServiceStatus } from '@/types/domain';

export function StationsPage() {
  const role = useAuth((s) => s.account?.role ?? 'USER');
  const isContractor = role === 'CONTRACTOR';
  const stations = useStations();
  const connectors = useConnectors();
  const setStatus = useSetServiceStatus();
  const del = useDeleteStation();

  const [q, setQ] = useState('');
  const [service, setService] = useState<ServiceStatus | 'ALL'>('ALL');
  const [online, setOnline] = useState<'ALL' | 'ON' | 'OFF'>('ALL');

  // Модалки/панели
  const [form, setForm] = useState<{ open: boolean; station?: ChargeBox }>({ open: false });
  const [detailId, setDetailId] = useState<string | null>(null);
  const [toDelete, setToDelete] = useState<ChargeBox | null>(null);

  const detailStation = useMemo(
    () => stations.data?.find((s) => s.chargeBoxId === detailId) ?? null,
    [stations.data, detailId],
  );

  const connByStation = useMemo(() => {
    const m = new Map<string, Connector[]>();
    for (const c of connectors.data ?? []) {
      const arr = m.get(c.chargeBoxId) ?? [];
      arr.push(c);
      m.set(c.chargeBoxId, arr);
    }
    return m;
  }, [connectors.data]);

  const rows = useMemo(() => {
    let list = stations.data ?? [];
    const term = q.trim().toLowerCase();
    if (term)
      list = list.filter(
        (s) =>
          s.chargeBoxId.toLowerCase().includes(term) ||
          (s.addressName ?? '').toLowerCase().includes(term) ||
          (s.ownerName ?? '').toLowerCase().includes(term),
      );
    if (service !== 'ALL') list = list.filter((s) => s.serviceStatus === service);
    if (online !== 'ALL') list = list.filter((s) => (online === 'ON' ? s.online : !s.online));
    return list;
  }, [stations.data, q, service, online]);

  const changeStatus = (s: ChargeBox, status: ServiceStatus) =>
    setStatus.mutate(
      { chargeBoxId: s.chargeBoxId, status },
      { onSuccess: () => toast.success('Статус обновлён', s.chargeBoxId), onError: (e) => toast.error('Ошибка', e.message) },
    );

  const confirmDelete = () => {
    if (!toDelete) return;
    del.mutate(toDelete.id, {
      onSuccess: () => {
        toast.success('Станция удалена', toDelete.chargeBoxId);
        setToDelete(null);
        setDetailId(null);
      },
      onError: (e) => toast.error('Не удалось удалить', e.message),
    });
  };

  const columns: Column<ChargeBox>[] = [
    {
      key: 'station',
      header: 'Станция',
      render: (s) => (
        <div className="flex items-start gap-3">
          <div className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
            <MapPin className="size-4" />
          </div>
          <div className="min-w-0">
            <div className="truncate font-medium">{s.addressName ?? s.chargeBoxId}</div>
            <div className="text-xs text-muted-foreground">
              {s.chargeBoxId} · {s.chargePointVendor} {s.chargePointModel}
            </div>
          </div>
        </div>
      ),
    },
    ...(!isContractor
      ? [
          {
            key: 'owner',
            header: 'Владелец',
            render: (s: ChargeBox) => <span className="text-sm">{s.ownerName ?? '—'}</span>,
          },
        ]
      : []),
    {
      key: 'connectors',
      header: 'Коннекторы',
      render: (s) => {
        const list = connByStation.get(s.chargeBoxId) ?? [];
        return (
          <div className="flex items-center gap-1.5">
            {list.map((c) => (
              <span
                key={c.id}
                title={`#${c.connectorId} · ${c.connectorTypeName} · ${c.status}`}
                className="size-2.5 rounded-full"
                style={{ background: connectorStatusColor[c.status] }}
              />
            ))}
            <span className="ml-1 text-xs text-muted-foreground">{list.length || s.connectorCount || 0}</span>
          </div>
        );
      },
    },
    { key: 'power', header: 'Мощность', render: (s) => <span className="font-medium">{formatKw(s.power)}</span> },
    {
      key: 'tariff',
      header: 'Тарифы',
      render: (s) => (
        <div className="text-sm">
          <div>{s.kwCost != null ? `${formatSom(s.kwCost)}/кВт·ч` : '—'}</div>
          <div className="text-xs text-muted-foreground">
            {s.bookingMinuteCost ? `${formatSom(s.bookingMinuteCost)}/мин бронь` : 'без брони'}
          </div>
        </div>
      ),
    },
    {
      key: 'status',
      header: 'Статус',
      render: (s) => (
        <div className="flex flex-col items-start gap-1.5">
          <OnlineDot online={s.online} />
          <ServiceStatusBadge status={s.serviceStatus} />
        </div>
      ),
    },
    {
      key: 'actions',
      header: '',
      headClassName: 'w-10',
      render: (s) => (
        <div onClick={(e) => e.stopPropagation()}>
          <Dropdown
            trigger={
              <Button variant="ghost" size="iconSm">
                <MoreVertical className="size-4" />
              </Button>
            }
          >
            {(close) => (
              <>
                <DropdownItem icon={<Eye />} onClick={() => { setDetailId(s.chargeBoxId); close(); }}>
                  Открыть
                </DropdownItem>
                <DropdownItem icon={<Pencil />} onClick={() => { setForm({ open: true, station: s }); close(); }}>
                  Редактировать
                </DropdownItem>
                <DropdownSeparator />
                <DropdownLabel>Статус эксплуатации</DropdownLabel>
                <DropdownItem icon={<Power />} onClick={() => { changeStatus(s, 'IN_SERVICE'); close(); }}>
                  Ввести в работу
                </DropdownItem>
                <DropdownItem icon={<Wrench />} onClick={() => { changeStatus(s, 'MAINTENANCE'); close(); }}>
                  На обслуживание
                </DropdownItem>
                <DropdownItem icon={<Power />} onClick={() => { changeStatus(s, 'OUT_OF_SERVICE'); close(); }}>
                  Вывести из работы
                </DropdownItem>
                <DropdownSeparator />
                <DropdownItem icon={<Trash2 />} danger onClick={() => { setToDelete(s); close(); }}>
                  Удалить
                </DropdownItem>
              </>
            )}
          </Dropdown>
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <PageHeader
        title="Станции"
        description={isContractor ? 'Ваши зарядные станции' : 'Все зарядные станции сети'}
        actions={
          USE_MOCK ? (
            <Button variant="accent" onClick={() => setForm({ open: true })}>
              <Plus className="size-4" />
              Добавить станцию
            </Button>
          ) : undefined
        }
      />

      <Card>
        <div className="flex flex-col gap-3 border-b border-border p-4 sm:flex-row sm:items-center">
          <div className="relative flex-1">
            <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              className="pl-9"
              placeholder="Поиск по адресу, ID или владельцу"
              value={q}
              onChange={(e) => setQ(e.target.value)}
            />
          </div>
          <Segmented
            value={online}
            onChange={setOnline}
            options={[
              { value: 'ALL', label: 'Все' },
              { value: 'ON', label: 'Онлайн' },
              { value: 'OFF', label: 'Оффлайн' },
            ]}
          />
          <Select value={service} onChange={(e) => setService(e.target.value as ServiceStatus | 'ALL')} className="sm:w-52">
            <option value="ALL">Все статусы</option>
            <option value="IN_SERVICE">В работе</option>
            <option value="MAINTENANCE">Обслуживание</option>
            <option value="OUT_OF_SERVICE">Выведены</option>
          </Select>
        </div>

        <DataTable
          columns={columns}
          rows={rows}
          loading={stations.isLoading}
          rowKey={(s) => s.id}
          onRowClick={(s) => setDetailId(s.chargeBoxId)}
          empty={<EmptyState icon={Search} title="Станции не найдены" message="Измените условия поиска или фильтры" />}
        />
        <div className="border-t border-border px-4 py-3 text-xs text-muted-foreground">
          Показано {rows.length} из {stations.data?.length ?? 0}
        </div>
      </Card>

      <StationForm open={form.open} station={form.station} onClose={() => setForm({ open: false })} />

      <StationDetailDrawer
        station={detailStation}
        onClose={() => setDetailId(null)}
        onEdit={(s) => { setDetailId(null); setForm({ open: true, station: s }); }}
        onDelete={(s) => { setDetailId(null); setToDelete(s); }}
      />

      <ConfirmDialog
        open={!!toDelete}
        onClose={() => setToDelete(null)}
        title="Удалить станцию?"
        message={toDelete ? `Станция ${toDelete.chargeBoxId} и её коннекторы будут удалены безвозвратно.` : ''}
        loading={del.isPending}
        onConfirm={confirmDelete}
      />
    </div>
  );
}
