import { useMemo, useState } from 'react';
import { ScrollText, Search } from 'lucide-react';
import { PageHeader } from '@/components/page-header';
import { Card } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { DataTable, type Column } from '@/components/data-table';
import { EmptyState } from '@/components/empty-state';
import { Drawer } from '@/components/ui/drawer';
import { DetailList } from '@/components/detail-list';
import { USE_MOCK } from '@/api/config';
import { useAuditEvents, type AuditEvent, type AuditQuery } from '@/api/audit';
import { formatDateTime } from '@/lib/format';

type Filters = Omit<AuditQuery, 'page' | 'size'>;

const EMPTY: Filters = {};
const PAGE_SIZE = 25;

function severityVariant(sev?: string): 'info' | 'warning' | 'danger' | 'default' {
  switch (sev) {
    case 'ERROR':
      return 'danger';
    case 'WARN':
      return 'warning';
    case 'INFO':
      return 'info';
    default:
      return 'default';
  }
}

function typeVariant(type?: string): 'primary' | 'info' | 'success' | 'outline' {
  switch (type) {
    case 'CHARGE_BOX':
    case 'CONNECTOR':
      return 'primary';
    case 'BALANCE':
      return 'success';
    case 'USER':
      return 'info';
    default:
      return 'outline';
  }
}

/** datetime-local (локальное время) → ISO-8601 instant для бэкенда. */
function toIso(local?: string): string | undefined {
  if (!local) return undefined;
  const d = new Date(local);
  return Number.isNaN(d.getTime()) ? undefined : d.toISOString();
}

export function AuditPage() {
  const [filters, setFilters] = useState<Filters>(EMPTY);
  const [fromLocal, setFromLocal] = useState('');
  const [toLocal, setToLocal] = useState('');
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<AuditEvent | null>(null);

  // Смена любого фильтра сбрасывает на первую страницу.
  function patch(part: Partial<Filters>) {
    setFilters((f) => ({ ...f, ...part }));
    setPage(0);
  }

  const query: AuditQuery = useMemo(
    () => ({
      ...filters,
      from: toIso(fromLocal),
      to: toIso(toLocal),
      page,
      size: PAGE_SIZE,
    }),
    [filters, fromLocal, toLocal, page],
  );

  const events = useAuditEvents(query);
  const data = events.data;
  const rows = data?.content ?? [];
  const total = data?.totalElements ?? 0;
  const totalPages = data?.totalPages ?? 0;
  const rangeFrom = total === 0 ? 0 : page * PAGE_SIZE + 1;
  const rangeTo = Math.min(total, page * PAGE_SIZE + rows.length);

  const columns: Column<AuditEvent>[] = [
    {
      key: 'time',
      header: 'Время',
      render: (e) => <span className="whitespace-nowrap text-sm">{e.timestamp ? formatDateTime(e.timestamp) : '—'}</span>,
    },
    {
      key: 'event',
      header: 'Событие',
      render: (e) => (
        <div className="space-y-1">
          <Badge variant={typeVariant(e.eventType)}>{e.eventType ?? '—'}</Badge>
          <div className="text-xs text-muted-foreground">{e.action ?? '—'}</div>
        </div>
      ),
    },
    {
      key: 'severity',
      header: 'Уровень',
      render: (e) => <Badge variant={severityVariant(e.severity)}>{e.severity ?? '—'}</Badge>,
    },
    { key: 'source', header: 'Источник', render: (e) => <span className="text-sm">{e.source ?? '—'}</span> },
    {
      key: 'entity',
      header: 'Субъект',
      render: (e) => <span className="font-mono text-xs">{e.entityId ?? '—'}</span>,
    },
    {
      key: 'actor',
      header: 'Актор',
      render: (e) => (
        <span className="block max-w-[160px] truncate font-mono text-xs" title={e.userId ?? undefined}>
          {e.userId ?? '—'}
        </span>
      ),
    },
    {
      key: 'message',
      header: 'Сообщение',
      render: (e) => (
        <span className="block max-w-[260px] truncate text-sm" title={e.message ?? undefined}>
          {e.message ?? '—'}
        </span>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <PageHeader title="Журнал аудита" description="События изменений: станции, коннекторы, пользователи, баланс" />

      {USE_MOCK ? (
        <Card>
          <div className="p-6 text-sm text-muted-foreground">
            Журнал аудита доступен только с реальным бэкендом. Запустите панель в режиме{' '}
            <code className="rounded bg-secondary px-1.5 py-0.5">VITE_DATA_SOURCE=api</code>.
          </div>
        </Card>
      ) : (
        <Card>
          <div className="space-y-3 border-b border-border p-4">
            <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
              <div className="relative flex-1">
                <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
                <Input
                  className="pl-9"
                  placeholder="Поиск по сообщению"
                  defaultValue={filters.q ?? ''}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') patch({ q: (e.target as HTMLInputElement).value });
                  }}
                  onBlur={(e) => patch({ q: e.target.value })}
                />
              </div>
              <Select
                value={filters.eventType ?? ''}
                onChange={(e) => patch({ eventType: e.target.value || undefined })}
                className="sm:w-44"
              >
                <option value="">Все типы</option>
                <option value="CHARGE_BOX">CHARGE_BOX</option>
                <option value="CONNECTOR">CONNECTOR</option>
                <option value="USER">USER</option>
                <option value="BALANCE">BALANCE</option>
              </Select>
              <Select
                value={filters.severity ?? ''}
                onChange={(e) => patch({ severity: e.target.value || undefined })}
                className="sm:w-40"
              >
                <option value="">Любой уровень</option>
                <option value="INFO">INFO</option>
                <option value="WARN">WARN</option>
                <option value="ERROR">ERROR</option>
              </Select>
            </div>
            <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
              <Input
                placeholder="Актор (userId)"
                defaultValue={filters.userId ?? ''}
                onBlur={(e) => patch({ userId: e.target.value || undefined })}
                className="sm:w-56"
              />
              <Input
                placeholder="Субъект (entityId)"
                defaultValue={filters.entityId ?? ''}
                onBlur={(e) => patch({ entityId: e.target.value || undefined })}
                className="sm:w-56"
              />
              <label className="flex items-center gap-2 text-xs text-muted-foreground">
                с
                <Input
                  type="datetime-local"
                  value={fromLocal}
                  onChange={(e) => {
                    setFromLocal(e.target.value);
                    setPage(0);
                  }}
                />
              </label>
              <label className="flex items-center gap-2 text-xs text-muted-foreground">
                по
                <Input
                  type="datetime-local"
                  value={toLocal}
                  onChange={(e) => {
                    setToLocal(e.target.value);
                    setPage(0);
                  }}
                />
              </label>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => {
                  setFilters(EMPTY);
                  setFromLocal('');
                  setToLocal('');
                  setPage(0);
                }}
              >
                Сбросить
              </Button>
            </div>
          </div>

          {events.isError ? (
            <div className="p-6 text-sm text-danger">
              Не удалось загрузить журнал. {(events.error as Error)?.message ?? ''} Требуется роль ADMIN или SPECIALIST.
            </div>
          ) : (
            <DataTable
              columns={columns}
              rows={rows}
              loading={events.isLoading}
              rowKey={(e) => e.eventId}
              onRowClick={(e) => setSelected(e)}
              empty={<EmptyState icon={ScrollText} title="Событий не найдено" />}
            />
          )}

          <div className="flex items-center justify-between gap-3 border-t border-border px-4 py-3 text-xs text-muted-foreground">
            <span>
              {total > 0 ? `${rangeFrom}–${rangeTo} из ${total}` : 'Нет событий'}
              {events.isFetching && !events.isLoading ? ' · обновление…' : ''}
            </span>
            <div className="flex items-center gap-2">
              <Button variant="outline" size="sm" disabled={page <= 0} onClick={() => setPage((p) => Math.max(0, p - 1))}>
                Назад
              </Button>
              <span>
                стр. {totalPages === 0 ? 0 : page + 1} из {totalPages}
              </span>
              <Button
                variant="outline"
                size="sm"
                disabled={page + 1 >= totalPages}
                onClick={() => setPage((p) => p + 1)}
              >
                Вперёд
              </Button>
            </div>
          </div>
        </Card>
      )}

      <Drawer
        open={!!selected}
        onClose={() => setSelected(null)}
        title={selected ? (selected.action ?? 'Событие') : ''}
        subtitle={selected ? (selected.eventType ?? '') : ''}
      >
        {selected && (
          <div className="space-y-4">
            <div className="flex flex-wrap gap-2">
              <Badge variant={typeVariant(selected.eventType)}>{selected.eventType ?? '—'}</Badge>
              <Badge variant={severityVariant(selected.severity)}>{selected.severity ?? '—'}</Badge>
            </div>
            <DetailList
              items={[
                { label: 'Время', value: selected.timestamp ? formatDateTime(selected.timestamp) : '—' },
                { label: 'Действие', value: selected.action },
                { label: 'Источник', value: selected.source },
                { label: 'Субъект', value: selected.entityId },
                { label: 'Актор', value: selected.userId },
                { label: 'IP', value: selected.ip },
                { label: 'Correlation', value: selected.correlationId },
                { label: 'Сообщение', value: selected.message },
                { label: 'eventId', value: <span className="font-mono text-xs">{selected.eventId}</span> },
              ]}
            />
            {selected.payload && Object.keys(selected.payload).length > 0 && (
              <div>
                <div className="mb-1.5 text-xs font-medium text-muted-foreground">payload</div>
                <pre className="overflow-x-auto rounded-lg border border-border bg-secondary/40 p-3 text-xs">
                  {JSON.stringify(selected.payload, null, 2)}
                </pre>
              </div>
            )}
          </div>
        )}
      </Drawer>
    </div>
  );
}
