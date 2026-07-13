import { useMemo, useState } from 'react';
import { Cable, Pencil, Plus, Search, Trash2 } from 'lucide-react';
import { PageHeader } from '@/components/page-header';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { ConfirmDialog } from '@/components/ui/confirm-dialog';
import { DataTable, type Column } from '@/components/data-table';
import { EmptyState } from '@/components/empty-state';
import { ConnectorChip } from '@/components/connector-chip';
import { ConnectorStatusBadge } from '@/components/status';
import { ConnectorForm } from './connector-form';
import { useConnectors, useDeleteConnector } from '@/api/hooks';
import { USE_MOCK } from '@/api/client';
import { toast } from '@/store/toast';
import { formatDate } from '@/lib/format';
import type { Connector, ConnectorStatus } from '@/types/domain';

export function ConnectorsPage() {
  const connectors = useConnectors();
  const del = useDeleteConnector();
  const [q, setQ] = useState('');
  const [status, setStatus] = useState<ConnectorStatus | 'ALL'>('ALL');
  const [form, setForm] = useState<{ open: boolean; connector?: Connector }>({ open: false });
  const [toDelete, setToDelete] = useState<Connector | null>(null);

  const rows = useMemo(() => {
    let list = connectors.data ?? [];
    const term = q.trim().toLowerCase();
    if (term)
      list = list.filter(
        (c) =>
          c.chargeBoxId.toLowerCase().includes(term) ||
          (c.connectorTypeName ?? '').toLowerCase().includes(term),
      );
    if (status !== 'ALL') list = list.filter((c) => c.status === status);
    return list;
  }, [connectors.data, q, status]);

  const confirmDelete = () => {
    if (!toDelete) return;
    del.mutate(toDelete.id, {
      onSuccess: () => {
        toast.success('Коннектор удалён', `${toDelete.chargeBoxId} · #${toDelete.connectorId}`);
        setToDelete(null);
      },
      onError: (e) => toast.error('Не удалось удалить', e.message),
    });
  };

  const columns: Column<Connector>[] = [
    {
      key: 'id',
      header: 'Коннектор',
      render: (c) => (
        <div>
          <div className="font-medium">
            {c.chargeBoxId} <span className="text-muted-foreground">· #{c.connectorId}</span>
          </div>
          <div className="text-xs text-muted-foreground">ID {c.id}</div>
        </div>
      ),
    },
    {
      key: 'type',
      header: 'Тип',
      render: (c) => (
        <div className="flex items-center gap-2">
          <ConnectorChip code={c.connectorTypeCode} name={c.connectorTypeName} status={c.status} />
          <span className="text-sm text-muted-foreground">{c.connectorTypeName}</span>
        </div>
      ),
    },
    { key: 'status', header: 'Статус', render: (c) => <ConnectorStatusBadge status={c.status} /> },
    { key: 'created', header: 'Создан', render: (c) => <span className="text-sm text-muted-foreground">{formatDate(c.createdAt)}</span> },
    {
      key: 'actions',
      header: '',
      headClassName: 'w-20',
      render: (c) => (
        <div className="flex justify-end gap-1">
          <Button variant="ghost" size="iconSm" onClick={() => setForm({ open: true, connector: c })}>
            <Pencil className="size-4" />
          </Button>
          <Button variant="ghost" size="iconSm" onClick={() => setToDelete(c)}>
            <Trash2 className="size-4 text-danger" />
          </Button>
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <PageHeader
        title="Коннекторы"
        description="Все коннекторы станций и их текущие статусы"
        actions={
          USE_MOCK ? (
            <Button variant="accent" onClick={() => setForm({ open: true })}>
              <Plus className="size-4" />
              Добавить коннектор
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
              placeholder="Поиск по станции или типу"
              value={q}
              onChange={(e) => setQ(e.target.value)}
            />
          </div>
          <Select value={status} onChange={(e) => setStatus(e.target.value as ConnectorStatus | 'ALL')} className="sm:w-52">
            <option value="ALL">Все статусы</option>
            <option value="Available">Свободен</option>
            <option value="Charging">Заряжает</option>
            <option value="Preparing">Подготовка</option>
            <option value="Reserved">Бронь</option>
            <option value="Unavailable">Недоступен</option>
            <option value="Faulted">Ошибка</option>
          </Select>
        </div>
        <DataTable
          columns={columns}
          rows={rows}
          loading={connectors.isLoading}
          rowKey={(c) => c.id}
          empty={<EmptyState icon={Cable} title="Коннекторы не найдены" />}
        />
        <div className="border-t border-border px-4 py-3 text-xs text-muted-foreground">
          Всего коннекторов: {rows.length}
        </div>
      </Card>

      <ConnectorForm open={form.open} connector={form.connector} onClose={() => setForm({ open: false })} />

      <ConfirmDialog
        open={!!toDelete}
        onClose={() => setToDelete(null)}
        title="Удалить коннектор?"
        message={toDelete ? `Коннектор #${toDelete.connectorId} станции ${toDelete.chargeBoxId} будет удалён.` : ''}
        loading={del.isPending}
        onConfirm={confirmDelete}
      />
    </div>
  );
}
