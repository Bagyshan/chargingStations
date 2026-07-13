import { useMemo, useState } from 'react';
import { BatteryCharging, Search } from 'lucide-react';
import { PageHeader } from '@/components/page-header';
import { Card } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { DataTable, type Column } from '@/components/data-table';
import { EmptyState } from '@/components/empty-state';
import { Avatar } from '@/components/ui/avatar';
import { Drawer } from '@/components/ui/drawer';
import { DetailList } from '@/components/detail-list';
import { TransactionStatusBadge } from '@/components/status';
import { useTransactions } from '@/api/hooks';
import { formatDateTime, formatKwh, formatNumber, formatSom } from '@/lib/format';
import type { Transaction, TransactionStatus } from '@/types/domain';

export function TransactionsPage() {
  const transactions = useTransactions();
  const [q, setQ] = useState('');
  const [status, setStatus] = useState<TransactionStatus | 'ALL'>('ALL');
  const [selected, setSelected] = useState<Transaction | null>(null);

  const rows = useMemo(() => {
    let list = transactions.data ?? [];
    const term = q.trim().toLowerCase();
    if (term)
      list = list.filter(
        (t) =>
          t.chargeBoxId.toLowerCase().includes(term) ||
          (t.userName ?? '').toLowerCase().includes(term) ||
          String(t.transactionId).includes(term),
      );
    if (status !== 'ALL') list = list.filter((t) => t.status === status);
    return list;
  }, [transactions.data, q, status]);

  const columns: Column<Transaction>[] = [
    {
      key: 'id',
      header: 'Сессия',
      render: (t) => (
        <div>
          <div className="font-medium">#{t.transactionId}</div>
          <div className="text-xs text-muted-foreground">{t.chargeBoxId} · #{t.connectorId}</div>
        </div>
      ),
    },
    {
      key: 'user',
      header: 'Пользователь',
      render: (t) => (
        <div className="flex items-center gap-2.5">
          <Avatar name={t.userName ?? t.userId} size={32} />
          <div className="text-sm font-medium">{t.userName ?? 'Пользователь'}</div>
        </div>
      ),
    },
    {
      key: 'energy',
      header: 'Энергия',
      render: (t) => <span className="font-semibold">{t.status === 'ACTIVE' ? '— идёт —' : formatKwh(t.transactionValue)}</span>,
    },
    {
      key: 'period',
      header: 'Период',
      render: (t) => (
        <div className="text-sm">
          <div>{formatDateTime(t.startTimestamp)}</div>
          <div className="text-xs text-muted-foreground">{t.stopTimestamp ? formatDateTime(t.stopTimestamp) : 'активна'}</div>
        </div>
      ),
    },
    {
      key: 'sum',
      header: 'Сумма',
      render: (t) => <span className="font-semibold">{t.totalSum ? formatSom(t.totalSum) : '—'}</span>,
    },
    { key: 'status', header: 'Статус', render: (t) => <TransactionStatusBadge status={t.status} /> },
  ];

  return (
    <div className="space-y-6">
      <PageHeader title="Зарядные сессии" description="История зарядок на станциях сети" />
      <Card>
        <div className="flex flex-col gap-3 border-b border-border p-4 sm:flex-row sm:items-center">
          <div className="relative flex-1">
            <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input className="pl-9" placeholder="Поиск по станции, сессии или пользователю" value={q} onChange={(e) => setQ(e.target.value)} />
          </div>
          <Select value={status} onChange={(e) => setStatus(e.target.value as TransactionStatus | 'ALL')} className="sm:w-48">
            <option value="ALL">Все статусы</option>
            <option value="ACTIVE">Активные</option>
            <option value="COMPLETED">Завершённые</option>
            <option value="CANCELLED">Отменённые</option>
            <option value="REJECTED">Отклонённые</option>
          </Select>
        </div>
        <DataTable
          columns={columns}
          rows={rows}
          loading={transactions.isLoading}
          rowKey={(t) => t.id}
          onRowClick={(t) => setSelected(t)}
          empty={<EmptyState icon={BatteryCharging} title="Сессий не найдено" />}
        />
        <div className="border-t border-border px-4 py-3 text-xs text-muted-foreground">Всего сессий: {rows.length}</div>
      </Card>

      <Drawer
        open={!!selected}
        onClose={() => setSelected(null)}
        title={selected ? `Сессия #${selected.transactionId}` : ''}
        subtitle={selected ? `${selected.chargeBoxId} · #${selected.connectorId}` : ''}
      >
        {selected && (
          <div className="space-y-4">
            <TransactionStatusBadge status={selected.status} />
            <DetailList
              items={[
                { label: 'Станция', value: selected.addressName ?? selected.chargeBoxId },
                { label: 'Коннектор', value: `#${selected.connectorId}` },
                { label: 'Пользователь', value: selected.userName ?? selected.userId },
                {
                  label: 'Энергия',
                  value: selected.status === 'ACTIVE' ? '— идёт —' : formatKwh(selected.transactionValue),
                },
                { label: 'Начало', value: formatDateTime(selected.startTimestamp) },
                { label: 'Окончание', value: selected.stopTimestamp ? formatDateTime(selected.stopTimestamp) : 'активна' },
                { label: 'Показания старт', value: formatNumber(selected.startValue) },
                { label: 'Показания стоп', value: selected.stopValue != null ? formatNumber(selected.stopValue) : '—' },
                { label: 'Сумма', value: selected.totalSum ? formatSom(selected.totalSum) : '—' },
                { label: 'Причина', value: selected.reason },
                { label: 'OCPP transactionId', value: selected.transactionId },
              ]}
            />
          </div>
        )}
      </Drawer>
    </div>
  );
}
