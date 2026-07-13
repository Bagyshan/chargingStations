import { useMemo, useState } from 'react';
import { CalendarClock, Search } from 'lucide-react';
import { PageHeader } from '@/components/page-header';
import { Card } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { DataTable, type Column } from '@/components/data-table';
import { EmptyState } from '@/components/empty-state';
import { Avatar } from '@/components/ui/avatar';
import { Drawer } from '@/components/ui/drawer';
import { DetailList } from '@/components/detail-list';
import { BookingStatusBadge } from '@/components/status';
import { useBookings } from '@/api/hooks';
import { formatDateTime, formatDuration, formatSom } from '@/lib/format';
import type { Booking, BookingStatus } from '@/types/domain';

export function BookingsPage() {
  const bookings = useBookings();
  const [q, setQ] = useState('');
  const [status, setStatus] = useState<BookingStatus | 'ALL'>('ALL');
  const [selected, setSelected] = useState<Booking | null>(null);

  const rows = useMemo(() => {
    let list = bookings.data ?? [];
    const term = q.trim().toLowerCase();
    if (term)
      list = list.filter(
        (b) =>
          b.stationId.toLowerCase().includes(term) ||
          (b.userName ?? '').toLowerCase().includes(term) ||
          (b.addressName ?? '').toLowerCase().includes(term),
      );
    if (status !== 'ALL') list = list.filter((b) => b.status === status);
    return list;
  }, [bookings.data, q, status]);

  const columns: Column<Booking>[] = [
    {
      key: 'user',
      header: 'Пользователь',
      render: (b) => (
        <div className="flex items-center gap-2.5">
          <Avatar name={b.userName ?? b.userId} size={32} />
          <div className="text-sm font-medium">{b.userName ?? 'Пользователь'}</div>
        </div>
      ),
    },
    {
      key: 'station',
      header: 'Станция',
      render: (b) => (
        <div>
          <div className="text-sm">{b.addressName ?? b.stationId}</div>
          <div className="text-xs text-muted-foreground">{b.stationId} · #{b.connectorId}</div>
        </div>
      ),
    },
    {
      key: 'period',
      header: 'Период',
      render: (b) => (
        <div className="text-sm">
          <div>{formatDateTime(b.startedAt)}</div>
          <div className="text-xs text-muted-foreground">{b.endedAt ? formatDateTime(b.endedAt) : 'активна'}</div>
        </div>
      ),
    },
    { key: 'duration', header: 'Длительность', render: (b) => formatDuration(b.totalMinutes) },
    {
      key: 'sum',
      header: 'Сумма',
      render: (b) => (
        <div>
          <div className="font-semibold">{formatSom(b.totalSum)}</div>
          <div className="text-xs text-muted-foreground">{formatSom(b.pricePerMinute)}/мин</div>
        </div>
      ),
    },
    { key: 'status', header: 'Статус', render: (b) => <BookingStatusBadge status={b.status} /> },
  ];

  return (
    <div className="space-y-6">
      <PageHeader title="Брони" description="Бронирования коннекторов на станциях" />
      <Card>
        <div className="flex flex-col gap-3 border-b border-border p-4 sm:flex-row sm:items-center">
          <div className="relative flex-1">
            <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input className="pl-9" placeholder="Поиск по станции или пользователю" value={q} onChange={(e) => setQ(e.target.value)} />
          </div>
          <Select value={status} onChange={(e) => setStatus(e.target.value as BookingStatus | 'ALL')} className="sm:w-48">
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
          loading={bookings.isLoading}
          rowKey={(b) => b.id}
          onRowClick={(b) => setSelected(b)}
          empty={<EmptyState icon={CalendarClock} title="Броней не найдено" />}
        />
        <div className="border-t border-border px-4 py-3 text-xs text-muted-foreground">Всего броней: {rows.length}</div>
      </Card>

      <Drawer
        open={!!selected}
        onClose={() => setSelected(null)}
        title={selected ? 'Бронирование' : ''}
        subtitle={selected ? `${selected.stationId} · #${selected.connectorId}` : ''}
      >
        {selected && (
          <div className="space-y-4">
            <BookingStatusBadge status={selected.status} />
            <DetailList
              items={[
                { label: 'Станция', value: selected.addressName ?? selected.stationId },
                { label: 'Коннектор', value: `#${selected.connectorId}` },
                { label: 'Пользователь', value: selected.userName ?? selected.userId },
                { label: 'Начало', value: formatDateTime(selected.startedAt) },
                { label: 'Окончание', value: selected.endedAt ? formatDateTime(selected.endedAt) : 'активна' },
                { label: 'Длительность', value: formatDuration(selected.totalMinutes) },
                { label: 'Тариф', value: `${formatSom(selected.pricePerMinute)}/мин` },
                { label: 'Сумма', value: formatSom(selected.totalSum) },
                { label: 'ID брони', value: selected.bookingId },
              ]}
            />
          </div>
        )}
      </Drawer>
    </div>
  );
}
