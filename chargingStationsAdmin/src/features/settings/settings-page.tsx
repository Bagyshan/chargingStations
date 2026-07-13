import { Database, Moon, Server, Sun } from 'lucide-react';
import { PageHeader } from '@/components/page-header';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Avatar } from '@/components/ui/avatar';
import { Segmented } from '@/components/ui/segmented';
import { RoleBadge } from '@/components/status';
import { USE_MOCK } from '@/api/client';
import { useAuth } from '@/store/auth';
import { useTheme } from '@/store/theme';

export function SettingsPage() {
  const account = useAuth((s) => s.account);
  const { theme, setTheme } = useTheme();
  const name = account
    ? `${account.firstName ?? ''} ${account.lastName ?? ''}`.trim() || account.email
    : '—';

  return (
    <div className="space-y-6">
      <PageHeader title="Настройки" description="Профиль, оформление и параметры подключения" />

      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>Профиль</CardTitle>
          </CardHeader>
          <CardContent className="flex items-center gap-4">
            <Avatar name={name} size={56} />
            <div>
              <div className="text-lg font-semibold">{name}</div>
              <div className="text-sm text-muted-foreground">{account?.email}</div>
              <div className="mt-1.5">{account && <RoleBadge role={account.role} />}</div>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Оформление</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2 text-sm">
                {theme === 'dark' ? <Moon className="size-4" /> : <Sun className="size-4" />}
                Тема интерфейса
              </div>
              <Segmented
                value={theme}
                onChange={setTheme}
                options={[
                  { value: 'light', label: 'Светлая' },
                  { value: 'dark', label: 'Тёмная' },
                ]}
              />
            </div>
          </CardContent>
        </Card>

        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>Подключение к API</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            <Row
              icon={<Database className="size-4" />}
              label="Источник данных"
              value={USE_MOCK ? 'Мок-слой (демо)' : 'API-шлюз (live)'}
              badge
            />
            <Row icon={<Server className="size-4" />} label="API-шлюз" value={import.meta.env.VITE_GATEWAY_URL ?? 'http://localhost:8010'} />
            <Row icon={<Server className="size-4" />} label="Keycloak realm" value="charging-stations" />
            <p className="pt-2 text-sm text-muted-foreground">
              В Фазе 2 мок-слой заменяется на реальные запросы к api-gateway-service с авторизацией
              через Keycloak. Все доменные модели уже соответствуют DTO бэкенда.
            </p>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

function Row({
  icon,
  label,
  value,
  badge,
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
  badge?: boolean;
}) {
  return (
    <div className="flex items-center justify-between rounded-lg border border-border px-3 py-2.5">
      <span className="flex items-center gap-2 text-sm text-muted-foreground">
        {icon}
        {label}
      </span>
      <span className={badge ? 'rounded-full bg-accent/15 px-2.5 py-0.5 text-xs font-medium text-accent' : 'font-mono text-sm'}>
        {value}
      </span>
    </div>
  );
}
