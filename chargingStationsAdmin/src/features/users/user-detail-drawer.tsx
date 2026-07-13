import { useState, type FormEvent } from 'react';
import { Loader2, Wallet } from 'lucide-react';
import { Drawer } from '@/components/ui/drawer';
import { Dialog } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Field } from '@/components/ui/field';
import { Avatar } from '@/components/ui/avatar';
import { Skeleton } from '@/components/ui/skeleton';
import { Badge } from '@/components/ui/badge';
import { DetailList } from '@/components/detail-list';
import { RoleBadge } from '@/components/status';
import { useTopUp, useUserBalance } from '@/api/hooks';
import { toast } from '@/store/toast';
import { formatDate, formatDateTime, formatSom } from '@/lib/format';
import type { User } from '@/types/domain';

export function UserDetailDrawer({ user, onClose }: { user: User | null; onClose: () => void }) {
  return (
    <Drawer
      open={!!user}
      onClose={onClose}
      title={user ? `${user.firstName ?? ''} ${user.lastName ?? ''}`.trim() || user.email : ''}
      subtitle={user?.email}
    >
      {user && <Content user={user} />}
    </Drawer>
  );
}

function Content({ user }: { user: User }) {
  const name = `${user.firstName ?? ''} ${user.lastName ?? ''}`.trim() || user.email;
  const balance = useUserBalance(user.keycloakId ?? undefined);
  const topUp = useTopUp();
  const [open, setOpen] = useState(false);
  const [amount, setAmount] = useState('');

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const value = Number(amount);
    if (!user.keycloakId) return toast.error('Нет keycloakId у пользователя');
    if (!value || value <= 0) return;
    topUp.mutate(
      { keycloakId: user.keycloakId, amount: value },
      {
        onSuccess: (b) => {
          toast.success('Баланс пополнен', `${name}: ${formatSom(b.balance)}`);
          setOpen(false);
          setAmount('');
        },
        onError: (err) => toast.error('Не удалось пополнить', err.message),
      },
    );
  };

  return (
    <div className="space-y-5">
      <div className="flex items-center gap-3">
        <Avatar name={name} size={48} />
        <div>
          <RoleBadge role={user.role} />
          <Badge variant={user.active ? 'success' : 'default'} className="ml-1.5">
            {user.active ? 'Активен' : 'Заблокирован'}
          </Badge>
        </div>
      </div>

      {/* Кошелёк */}
      <div className="rounded-xl border border-border p-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <Wallet className="size-4" />
            Баланс кошелька
          </div>
          <Button size="sm" variant="accent" onClick={() => setOpen(true)} disabled={!user.keycloakId}>
            Пополнить
          </Button>
        </div>
        <div className="mt-2 text-2xl font-bold">
          {balance.isLoading ? (
            <Skeleton className="h-8 w-32" />
          ) : balance.data ? (
            formatSom(balance.data.balance)
          ) : (
            <span className="text-base font-medium text-muted-foreground">Кошелёк не создан</span>
          )}
        </div>
        {!user.keycloakId && (
          <p className="mt-1 text-xs text-warning">Нет keycloakId — пополнение недоступно</p>
        )}
      </div>

      <DetailList
        items={[
          { label: 'Email', value: user.email },
          { label: 'Телефон', value: user.phone },
          { label: 'Роль', value: <RoleBadge role={user.role} /> },
          { label: 'Email подтверждён', value: user.emailVerified ? 'Да' : 'Нет' },
          { label: 'Телефон подтверждён', value: user.phoneVerified ? 'Да' : 'Нет' },
          { label: 'Регистрация', value: formatDate(user.createdAt) },
          { label: 'Последний вход', value: user.lastLoginAt ? formatDateTime(user.lastLoginAt) : '—' },
          { label: 'Keycloak ID', value: user.keycloakId ? <span className="font-mono text-xs">{user.keycloakId}</span> : '—' },
        ]}
      />

      <Dialog
        open={open}
        onClose={() => setOpen(false)}
        title="Пополнение баланса"
        description={`${name} · без оплаты (ADMIN)`}
        footer={
          <>
            <Button variant="ghost" onClick={() => setOpen(false)} disabled={topUp.isPending}>
              Отмена
            </Button>
            <Button variant="accent" form="topup-form" type="submit" disabled={topUp.isPending}>
              {topUp.isPending && <Loader2 className="size-4 animate-spin" />}
              Пополнить
            </Button>
          </>
        }
      >
        <form id="topup-form" onSubmit={submit}>
          <Field label="Сумма, сом" required hint="Ручное зачисление в обход O!Dengi">
            <Input
              type="number"
              min="1"
              step="1"
              autoFocus
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              placeholder="500"
            />
          </Field>
        </form>
      </Dialog>
    </div>
  );
}
