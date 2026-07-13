import { useState } from 'react';
import { BatteryCharging, MapPin, Pencil, Plus, Trash2 } from 'lucide-react';
import { Drawer } from '@/components/ui/drawer';
import { Button } from '@/components/ui/button';
import { Segmented } from '@/components/ui/segmented';
import { Skeleton } from '@/components/ui/skeleton';
import { ConfirmDialog } from '@/components/ui/confirm-dialog';
import { ConnectorChip } from '@/components/connector-chip';
import { ConnectorStatusBadge, OnlineDot } from '@/components/status';
import { ConnectorForm } from '@/features/connectors/connector-form';
import { useConnectors, useDeleteConnector, useSetServiceStatus, useTransactions } from '@/api/hooks';
import { USE_MOCK } from '@/api/client';
import { toast } from '@/store/toast';
import { formatDateTime, formatKw, formatKwh, formatSom } from '@/lib/format';
import type { ChargeBox, Connector, ServiceStatus } from '@/types/domain';

export function StationDetailDrawer({
  station,
  onClose,
  onEdit,
  onDelete,
}: {
  station: ChargeBox | null;
  onClose: () => void;
  onEdit: (s: ChargeBox) => void;
  onDelete: (s: ChargeBox) => void;
}) {
  return (
    <Drawer
      open={!!station}
      onClose={onClose}
      title={station?.addressName ?? station?.chargeBoxId ?? ''}
      subtitle={station ? `${station.chargeBoxId} · ${station.chargePointVendor ?? ''} ${station.chargePointModel ?? ''}` : ''}
    >
      {station && <Content station={station} onEdit={onEdit} onDelete={onDelete} />}
    </Drawer>
  );
}

function Content({
  station,
  onEdit,
  onDelete,
}: {
  station: ChargeBox;
  onEdit: (s: ChargeBox) => void;
  onDelete: (s: ChargeBox) => void;
}) {
  const connectors = useConnectors(station.chargeBoxId);
  const transactions = useTransactions();
  const setStatus = useSetServiceStatus();
  const delConnector = useDeleteConnector();

  const [connectorForm, setConnectorForm] = useState<{ open: boolean; connector?: Connector }>({ open: false });
  const [toDelete, setToDelete] = useState<Connector | null>(null);

  const recent = (transactions.data ?? []).filter((t) => t.chargeBoxId === station.chargeBoxId).slice(0, 5);

  const changeStatus = (status: ServiceStatus) =>
    setStatus.mutate(
      { chargeBoxId: station.chargeBoxId, status },
      {
        onSuccess: () => toast.success('Статус обновлён'),
        onError: (e) => toast.error('Ошибка', e.message),
      },
    );

  return (
    <div className="space-y-6">
      {/* Статус */}
      <section className="space-y-3">
        <div className="flex items-center justify-between">
          <OnlineDot online={station.online} />
          <span className="text-sm text-muted-foreground">
            {station.connectorCount ?? connectors.data?.length ?? 0} коннектор(ов)
          </span>
        </div>
        <Segmented
          className="w-full"
          size="sm"
          value={station.serviceStatus ?? 'IN_SERVICE'}
          onChange={changeStatus}
          options={[
            { value: 'IN_SERVICE', label: 'В работе' },
            { value: 'MAINTENANCE', label: 'Обслуживание' },
            { value: 'OUT_OF_SERVICE', label: 'Выведена' },
          ]}
        />
      </section>

      {/* Тарифы */}
      <section className="grid grid-cols-3 gap-2">
        <Metric label="Мощность" value={formatKw(station.power)} />
        <Metric label="Зарядка" value={station.kwCost != null ? `${formatSom(station.kwCost, false)}/кВт·ч` : '—'} />
        <Metric label="Бронь" value={station.bookingMinuteCost ? `${formatSom(station.bookingMinuteCost, false)}/мин` : '—'} />
      </section>

      {/* Оборудование */}
      <section>
        <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">Оборудование</h3>
        <div className="divide-y divide-border rounded-lg border border-border">
          <Info label="Производитель" value={station.chargePointVendor} />
          <Info label="Модель" value={station.chargePointModel} />
          <Info label="OCPP" value={station.ocppProtocol} />
          <Info label="Прошивка" value={station.firmwareVersion} />
          <Info label="OCPP тег" value={station.ocppTag} />
          <Info label="Серийный №" value={station.chargePointSerialNumber} />
          <Info
            label="Координаты"
            value={station.latitude != null ? `${station.latitude?.toFixed(4)}, ${station.longitude?.toFixed(4)}` : null}
          />
        </div>
      </section>

      {/* Коннекторы */}
      <section>
        <div className="mb-2 flex items-center justify-between">
          <h3 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Коннекторы</h3>
          {USE_MOCK && (
            <Button variant="outline" size="sm" onClick={() => setConnectorForm({ open: true })}>
              <Plus className="size-4" />
              Добавить
            </Button>
          )}
        </div>
        <div className="space-y-2">
          {connectors.isLoading ? (
            <Skeleton className="h-14 rounded-lg" />
          ) : connectors.data?.length ? (
            connectors.data.map((c) => (
              <div key={c.id} className="flex items-center gap-3 rounded-lg border border-border p-3">
                <ConnectorChip code={c.connectorTypeCode} name={c.connectorTypeName} status={c.status} />
                <div className="min-w-0 flex-1">
                  <div className="text-sm font-medium">
                    #{c.connectorId} · {c.connectorTypeName ?? 'Без типа'}
                  </div>
                  <ConnectorStatusBadge status={c.status} />
                </div>
                <button
                  onClick={() => setConnectorForm({ open: true, connector: c })}
                  className="text-muted-foreground hover:text-foreground"
                >
                  <Pencil className="size-4" />
                </button>
                <button onClick={() => setToDelete(c)} className="text-muted-foreground hover:text-danger">
                  <Trash2 className="size-4" />
                </button>
              </div>
            ))
          ) : (
            <p className="rounded-lg border border-dashed border-border py-4 text-center text-sm text-muted-foreground">
              Нет коннекторов
            </p>
          )}
        </div>
      </section>

      {/* Недавние зарядки */}
      <section>
        <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">Недавние зарядки</h3>
        {recent.length ? (
          <div className="space-y-1.5">
            {recent.map((t) => (
              <div key={t.id} className="flex items-center gap-2 text-sm">
                <BatteryCharging className="size-4 text-primary" />
                <span className="text-muted-foreground">{formatDateTime(t.startTimestamp)}</span>
                <span className="ml-auto font-medium">
                  {t.status === 'ACTIVE' ? '— идёт —' : formatKwh(t.transactionValue)}
                </span>
              </div>
            ))}
          </div>
        ) : (
          <p className="text-sm text-muted-foreground">Пока нет сессий</p>
        )}
      </section>

      {/* Действия */}
      <section className="flex gap-2 border-t border-border pt-4">
        <Button className="flex-1" onClick={() => onEdit(station)}>
          <Pencil className="size-4" />
          Редактировать
        </Button>
        <Button variant="outline" onClick={() => onEdit(station)} className="shrink-0">
          <MapPin className="size-4" />
        </Button>
        <Button variant="danger" onClick={() => onDelete(station)} className="shrink-0">
          <Trash2 className="size-4" />
        </Button>
      </section>

      <ConnectorForm
        open={connectorForm.open}
        connector={connectorForm.connector}
        defaultChargeBoxId={station.chargeBoxId}
        onClose={() => setConnectorForm({ open: false })}
      />

      <ConfirmDialog
        open={!!toDelete}
        onClose={() => setToDelete(null)}
        title="Удалить коннектор?"
        message={toDelete ? `Коннектор #${toDelete.connectorId} станции ${station.chargeBoxId} будет удалён.` : ''}
        loading={delConnector.isPending}
        onConfirm={() => {
          if (!toDelete) return;
          delConnector.mutate(toDelete.id, {
            onSuccess: () => {
              toast.success('Коннектор удалён');
              setToDelete(null);
            },
            onError: (e) => toast.error('Ошибка', e.message),
          });
        }}
      />
    </div>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg bg-muted/50 p-3 text-center">
      <div className="text-xs text-muted-foreground">{label}</div>
      <div className="mt-0.5 text-sm font-semibold">{value}</div>
    </div>
  );
}

function Info({ label, value }: { label: string; value?: string | null }) {
  return (
    <div className="flex items-center justify-between px-3 py-2 text-sm">
      <span className="text-muted-foreground">{label}</span>
      <span className="font-medium">{value || '—'}</span>
    </div>
  );
}
