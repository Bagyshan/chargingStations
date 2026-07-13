import { useMemo, useState } from 'react';
import { CheckCircle2, ChevronDown, Mail, Phone, Search, ShieldAlert, UserCog, Users } from 'lucide-react';
import { PageHeader } from '@/components/page-header';
import { StatCard } from '@/components/stat-card';
import { Card } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Button } from '@/components/ui/button';
import { Switch } from '@/components/ui/switch';
import { Avatar } from '@/components/ui/avatar';
import { Dropdown, DropdownItem, DropdownLabel } from '@/components/ui/dropdown';
import { DataTable, type Column } from '@/components/data-table';
import { EmptyState } from '@/components/empty-state';
import { RoleBadge } from '@/components/status';
import { useChangeUserRole, useSetUserActive, useUsers } from '@/api/hooks';
import { useAuth } from '@/store/auth';
import { toast } from '@/store/toast';
import { UserDetailDrawer } from './user-detail-drawer';
import { downloadCsv } from '@/lib/csv';
import { formatDate, formatSom } from '@/lib/format';
import { ROLE_LABELS, type Role, type User } from '@/types/domain';

const ROLES: Role[] = ['ADMIN', 'SPECIALIST', 'CONTRACTOR', 'USER'];

export function UsersPage() {
  const role = useAuth((s) => s.account?.role ?? 'USER');
  const users = useUsers();
  const changeRole = useChangeUserRole();
  const setActive = useSetUserActive();
  const [q, setQ] = useState('');
  const [roleFilter, setRoleFilter] = useState<Role | 'ALL'>('ALL');
  const [selected, setSelected] = useState<User | null>(null);

  const onExport = () =>
    downloadCsv(
      'batenergy-users.csv',
      ['Имя', 'Email', 'Телефон', 'Роль', 'Активен', 'Email подтверждён', 'Регистрация'],
      rows.map((u) => [
        `${u.firstName ?? ''} ${u.lastName ?? ''}`.trim(),
        u.email,
        u.phone ?? '',
        ROLE_LABELS[u.role],
        u.active ? 'да' : 'нет',
        u.emailVerified ? 'да' : 'нет',
        formatDate(u.createdAt),
      ]),
    );

  const rows = useMemo(() => {
    let list = users.data ?? [];
    const term = q.trim().toLowerCase();
    if (term)
      list = list.filter(
        (u) =>
          u.email.toLowerCase().includes(term) ||
          `${u.firstName} ${u.lastName}`.toLowerCase().includes(term) ||
          (u.phone ?? '').includes(term),
      );
    if (roleFilter !== 'ALL') list = list.filter((u) => u.role === roleFilter);
    return list;
  }, [users.data, q, roleFilter]);

  if (role !== 'ADMIN') {
    return (
      <div className="space-y-6">
        <PageHeader title="Пользователи" />
        <Card>
          <EmptyState
            icon={ShieldAlert}
            title="Доступ ограничен"
            message="Раздел пользователей доступен только администраторам системы."
          />
        </Card>
      </div>
    );
  }

  const total = users.data?.length ?? 0;
  const active = users.data?.filter((u) => u.active).length ?? 0;
  const contractors = users.data?.filter((u) => u.role === 'CONTRACTOR').length ?? 0;
  const staff = users.data?.filter((u) => u.role === 'ADMIN' || u.role === 'SPECIALIST').length ?? 0;

  const name = (u: User) => `${u.firstName ?? ''} ${u.lastName ?? ''}`.trim() || u.email;

  const columns: Column<User>[] = [
    {
      key: 'user',
      header: 'Пользователь',
      render: (u) => (
        <div className="flex items-center gap-3">
          <Avatar name={name(u)} size={38} />
          <div className="min-w-0">
            <div className="truncate font-medium">{name(u)}</div>
            <div className="truncate text-xs text-muted-foreground">{u.email}</div>
          </div>
        </div>
      ),
    },
    { key: 'phone', header: 'Телефон', render: (u) => <span className="text-sm">{u.phone ?? '—'}</span> },
    {
      key: 'role',
      header: 'Роль',
      render: (u) => (
        <div onClick={(e) => e.stopPropagation()}>
        <Dropdown
          trigger={
            <button className="inline-flex items-center gap-1">
              <RoleBadge role={u.role} />
              <ChevronDown className="size-3.5 text-muted-foreground" />
            </button>
          }
          align="start"
        >
          {(close) => (
            <>
              <DropdownLabel>Изменить роль</DropdownLabel>
              {ROLES.map((r) => (
                <DropdownItem
                  key={r}
                  icon={<UserCog />}
                  onClick={() => {
                    changeRole.mutate(
                      { id: u.id, role: r },
                      {
                        onSuccess: () => toast.success('Роль изменена', `${u.email} → ${ROLE_LABELS[r]}`),
                        onError: (e) => toast.error('Ошибка', e.message),
                      },
                    );
                    close();
                  }}
                >
                  {ROLE_LABELS[r]}
                </DropdownItem>
              ))}
            </>
          )}
        </Dropdown>
        </div>
      ),
    },
    {
      key: 'verify',
      header: 'Верификация',
      render: (u) => (
        <div className="flex items-center gap-1.5">
          <span
            className={`inline-flex items-center gap-1 rounded-md px-1.5 py-0.5 text-xs ${u.emailVerified ? 'text-success' : 'text-muted-foreground'}`}
            title={u.emailVerified ? 'Email подтверждён' : 'Email не подтверждён'}
          >
            <Mail className="size-3.5" />
            {u.emailVerified && <CheckCircle2 className="size-3" />}
          </span>
          <span
            className={`inline-flex items-center gap-1 rounded-md px-1.5 py-0.5 text-xs ${u.phoneVerified ? 'text-success' : 'text-muted-foreground'}`}
            title={u.phoneVerified ? 'Телефон подтверждён' : 'Телефон не подтверждён'}
          >
            <Phone className="size-3.5" />
            {u.phoneVerified && <CheckCircle2 className="size-3" />}
          </span>
        </div>
      ),
    },
    { key: 'balance', header: 'Баланс', render: (u) => <span className="font-medium">{u.balance != null ? formatSom(u.balance) : '—'}</span> },
    { key: 'created', header: 'Регистрация', render: (u) => <span className="text-sm text-muted-foreground">{formatDate(u.createdAt)}</span> },
    {
      key: 'active',
      header: 'Активен',
      headClassName: 'text-right',
      className: 'text-right',
      render: (u) => (
        <div className="flex justify-end" onClick={(e) => e.stopPropagation()}>
          <Switch
            checked={u.active}
            onChange={(v) =>
              setActive.mutate(
                { id: u.id, active: v },
                {
                  onSuccess: () => toast.success(v ? 'Пользователь активирован' : 'Пользователь деактивирован', u.email),
                  onError: (e) => toast.error('Ошибка', e.message),
                },
              )
            }
          />
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <PageHeader title="Пользователи" description="Управление пользователями, ролями и доступом" />

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard label="Всего пользователей" value={String(total)} icon={Users} tone="primary" loading={users.isLoading} />
        <StatCard label="Активных" value={String(active)} icon={CheckCircle2} tone="success" loading={users.isLoading} />
        <StatCard label="Контрагентов" value={String(contractors)} icon={UserCog} tone="accent" loading={users.isLoading} />
        <StatCard label="Персонал" value={String(staff)} icon={ShieldAlert} tone="info" loading={users.isLoading} />
      </div>

      <Card>
        <div className="flex flex-col gap-3 border-b border-border p-4 sm:flex-row sm:items-center">
          <div className="relative flex-1">
            <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input className="pl-9" placeholder="Поиск по имени, email или телефону" value={q} onChange={(e) => setQ(e.target.value)} />
          </div>
          <Select value={roleFilter} onChange={(e) => setRoleFilter(e.target.value as Role | 'ALL')} className="sm:w-48">
            <option value="ALL">Все роли</option>
            {ROLES.map((r) => (
              <option key={r} value={r}>
                {ROLE_LABELS[r]}
              </option>
            ))}
          </Select>
          <Button variant="outline" onClick={onExport}>Экспорт</Button>
        </div>
        <DataTable
          columns={columns}
          rows={rows}
          loading={users.isLoading}
          rowKey={(u) => u.id}
          onRowClick={(u) => setSelected(u)}
          empty={<EmptyState icon={Users} title="Пользователи не найдены" />}
        />
        <div className="border-t border-border px-4 py-3 text-xs text-muted-foreground">Показано {rows.length} из {total}</div>
      </Card>

      <UserDetailDrawer user={selected} onClose={() => setSelected(null)} />
    </div>
  );
}
