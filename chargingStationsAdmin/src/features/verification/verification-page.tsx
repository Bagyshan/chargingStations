import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  CheckCircle2,
  Copy,
  KeyRound,
  Link2,
  RefreshCw,
  ShieldAlert,
  ShieldCheck,
} from 'lucide-react';
import { PageHeader } from '@/components/page-header';
import { Card } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { EmptyState } from '@/components/empty-state';
import { request } from '@/api/http';
import { SERVICE } from '@/api/config';
import { useAuth } from '@/store/auth';
import { toast } from '@/store/toast';
import { formatDate } from '@/lib/format';

/** Запись обзора подтверждения email (совпадает с AdminVerificationEntry на бэкенде). */
interface VerificationEntry {
  userId: number;
  email: string;
  emailVerified: boolean;
  token: string;
  verifyLink: string;
  expiresAt: string | null;
  used: boolean;
  expired: boolean;
}

const QK = ['admin', 'verification-tokens'];

export function VerificationPage() {
  const role = useAuth((s) => s.account?.role ?? 'USER');
  const qc = useQueryClient();
  const [q, setQ] = useState('');

  const query = useQuery({
    queryKey: QK,
    enabled: role === 'ADMIN',
    queryFn: () =>
      request<VerificationEntry[]>(`${SERVICE.user}/api/v1/admin/verification-tokens`, {
        unwrap: true,
      }),
  });

  const activate = useMutation({
    mutationFn: (userId: number) =>
      request<void>(`${SERVICE.user}/api/v1/admin/users/${userId}/verify-email`, {
        method: 'POST',
      }),
    onSuccess: () => {
      toast.success('Аккаунт активирован', 'Email подтверждён администратором');
      qc.invalidateQueries({ queryKey: QK });
    },
    onError: (e: Error) => toast.error('Не удалось активировать', e.message),
  });

  const rows = useMemo(() => {
    const list = query.data ?? [];
    const term = q.trim().toLowerCase();
    return term ? list.filter((r) => r.email.toLowerCase().includes(term)) : list;
  }, [query.data, q]);

  const copy = async (text: string, label: string) => {
    try {
      await navigator.clipboard.writeText(text);
      toast.success('Скопировано', label);
    } catch {
      toast.error('Не удалось скопировать');
    }
  };

  if (role !== 'ADMIN') {
    return (
      <div className="space-y-6">
        <PageHeader title="OTP и активация" />
        <Card>
          <EmptyState
            icon={ShieldAlert}
            title="Доступ ограничен"
            message="Раздел доступен только администраторам."
          />
        </Card>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="OTP и активация"
        actions={
          <Button
            variant="outline"
            onClick={() => query.refetch()}
            disabled={query.isFetching}
          >
            <RefreshCw className={query.isFetching ? 'animate-spin' : ''} /> Обновить
          </Button>
        }
      />

      <Card className="overflow-hidden p-0">
        <div className="border-b border-border p-4">
          <Input
            placeholder="Поиск по email…"
            value={q}
            onChange={(e) => setQ(e.target.value)}
          />
        </div>

        {query.isLoading ? (
          <div className="p-8 text-center text-sm text-muted-foreground">Загрузка…</div>
        ) : query.isError ? (
          <div className="p-8 text-center text-sm text-danger">
            Не удалось загрузить: {(query.error as Error)?.message}
          </div>
        ) : rows.length === 0 ? (
          <EmptyState
            icon={KeyRound}
            title="Нет токенов подтверждения"
            message="Пока никто не регистрировался, либо все письма уже подтверждены."
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="border-b border-border text-left text-xs uppercase tracking-wide text-muted-foreground">
                <tr>
                  <th className="px-4 py-3 font-medium">Email</th>
                  <th className="px-4 py-3 font-medium">Статус</th>
                  <th className="px-4 py-3 font-medium">OTP-код</th>
                  <th className="px-4 py-3 font-medium">Ссылка</th>
                  <th className="px-4 py-3 font-medium">Истекает</th>
                  <th className="px-4 py-3 text-right font-medium">Действие</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => (
                  <tr
                    key={`${r.userId}-${r.token}`}
                    className="border-b border-border/60 last:border-0 hover:bg-secondary/40"
                  >
                    <td className="px-4 py-3 font-medium">{r.email}</td>
                    <td className="px-4 py-3">
                      {r.emailVerified ? (
                        <span className="inline-flex items-center gap-1 text-emerald-600">
                          <ShieldCheck className="size-4" /> Подтверждён
                        </span>
                      ) : r.used ? (
                        <span className="text-muted-foreground">Использован</span>
                      ) : r.expired ? (
                        <span className="text-muted-foreground">Истёк</span>
                      ) : (
                        <span className="inline-flex items-center gap-1 text-amber-600">
                          <ShieldAlert className="size-4" /> Ждёт подтверждения
                        </span>
                      )}
                    </td>
                    <td className="px-4 py-3">
                      <button
                        type="button"
                        title="Скопировать код"
                        onClick={() => copy(r.token, 'OTP-код')}
                        className="inline-flex items-center gap-1.5 rounded-md bg-secondary px-2 py-1 font-mono text-xs hover:bg-secondary/70"
                      >
                        {r.token.slice(0, 8)}… <Copy className="size-3.5" />
                      </button>
                    </td>
                    <td className="px-4 py-3">
                      <button
                        type="button"
                        title="Скопировать ссылку подтверждения"
                        onClick={() => copy(r.verifyLink, 'Ссылка подтверждения')}
                        className="inline-flex items-center gap-1.5 text-xs text-primary hover:underline"
                      >
                        <Link2 className="size-3.5" /> Копировать ссылку
                      </button>
                    </td>
                    <td className="px-4 py-3 text-muted-foreground">
                      {r.expiresAt ? formatDate(r.expiresAt) : '—'}
                    </td>
                    <td className="px-4 py-3 text-right">
                      {r.emailVerified ? (
                        <span className="inline-flex items-center gap-1 text-xs text-emerald-600">
                          <CheckCircle2 className="size-4" /> Активен
                        </span>
                      ) : (
                        <Button
                          size="sm"
                          onClick={() => activate.mutate(r.userId)}
                          disabled={activate.isPending}
                        >
                          Активировать
                        </Button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  );
}
