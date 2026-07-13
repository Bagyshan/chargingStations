import { useState, type FormEvent } from 'react';
import { useNavigate } from '@tanstack/react-router';
import { BarChart3, Loader2, LockKeyhole, Mail, ShieldCheck, Zap } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Logo } from '@/components/layout/logo';
import { RoleBadge } from '@/components/status';
import { DEMO_ACCOUNTS } from '@/mock/api';
import { USE_MOCK } from '@/api/client';
import { useAuth } from '@/store/auth';

export function LoginPage() {
  const navigate = useNavigate();
  const login = useAuth((s) => s.login);
  const loading = useAuth((s) => s.loading);
  const error = useAuth((s) => s.error);

  const [email, setEmail] = useState(USE_MOCK ? 'admin@batenergy.kg' : '');
  const [password, setPassword] = useState(USE_MOCK ? 'admin' : '');

  async function submit(e: FormEvent) {
    e.preventDefault();
    try {
      await login(email, password);
      navigate({ to: '/' });
    } catch {
      /* ошибка показана из стора */
    }
  }

  async function quick(demoEmail: string, demoPassword: string) {
    setEmail(demoEmail);
    setPassword(demoPassword);
    try {
      await login(demoEmail, demoPassword);
      navigate({ to: '/' });
    } catch {
      /* no-op */
    }
  }

  return (
    <div className="grid min-h-screen lg:grid-cols-2">
      {/* Брендовая панель */}
      <div className="relative hidden overflow-hidden bg-primary lg:flex lg:flex-col lg:justify-between lg:p-12 text-primary-foreground">
        <div
          className="pointer-events-none absolute inset-0 opacity-30"
          style={{
            background:
              'radial-gradient(60% 60% at 80% 10%, oklch(0.79 0.15 68 / 0.6), transparent), radial-gradient(50% 50% at 10% 90%, oklch(0.7 0.16 200 / 0.5), transparent)',
          }}
        />
        <div className="relative flex items-center gap-2.5">
          <div className="flex size-10 items-center justify-center rounded-xl bg-white/15 backdrop-blur">
            <Zap className="size-5 text-accent" />
          </div>
          <span className="text-lg font-bold">BatEnergy</span>
        </div>

        <div className="relative">
          <h1 className="max-w-md text-4xl font-bold leading-tight">
            Единая панель управления сетью зарядных станций
          </h1>
          <p className="mt-4 max-w-md text-primary-foreground/80">
            Станции, коннекторы, брони, зарядные сессии, тарифы и аналитика выручки — в одном месте,
            с учётом ролей администратора, специалиста и контрагента.
          </p>
          <div className="mt-8 flex flex-wrap gap-3">
            {[
              { icon: Zap, text: 'Управление станциями' },
              { icon: BarChart3, text: 'Аналитика и выручка' },
              { icon: ShieldCheck, text: 'Ролевой доступ' },
            ].map((f) => (
              <div
                key={f.text}
                className="flex items-center gap-2 rounded-lg bg-white/10 px-3 py-2 text-sm backdrop-blur"
              >
                <f.icon className="size-4 text-accent" />
                {f.text}
              </div>
            ))}
          </div>
        </div>

        <div className="relative text-sm text-primary-foreground/60">
          © 2026 BatEnergy · Charging Stations Platform
        </div>
      </div>

      {/* Форма */}
      <div className="flex items-center justify-center p-6">
        <div className="w-full max-w-sm">
          <div className="mb-8 lg:hidden">
            <Logo />
          </div>
          <h2 className="text-2xl font-bold tracking-tight">Вход в панель</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            Войдите под своей ролью, чтобы продолжить.
          </p>

          <form onSubmit={submit} className="mt-6 space-y-4">
            <div className="space-y-1.5">
              <label className="text-sm font-medium">Email</label>
              <div className="relative">
                <Mail className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
                <Input
                  className="pl-9"
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="you@batenergy.kg"
                  autoComplete="username"
                />
              </div>
            </div>
            <div className="space-y-1.5">
              <label className="text-sm font-medium">Пароль</label>
              <div className="relative">
                <LockKeyhole className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
                <Input
                  className="pl-9"
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••"
                  autoComplete="current-password"
                />
              </div>
            </div>

            {error && (
              <div className="rounded-md bg-danger/10 px-3 py-2 text-sm text-danger">{error}</div>
            )}

            <Button type="submit" size="lg" className="w-full" disabled={loading}>
              {loading && <Loader2 className="size-4 animate-spin" />}
              Войти
            </Button>
          </form>

          {USE_MOCK && (
            <>
              <div className="my-6 flex items-center gap-3 text-xs text-muted-foreground">
                <div className="h-px flex-1 bg-border" />
                демо-доступ в один клик
                <div className="h-px flex-1 bg-border" />
              </div>

              <div className="space-y-2">
                {DEMO_ACCOUNTS.map((a) => (
                  <button
                    key={a.role}
                    onClick={() => quick(a.email, a.password)}
                    disabled={loading}
                    className="flex w-full items-center justify-between rounded-lg border border-border bg-card px-3 py-2.5 text-left transition-colors hover:border-primary/40 hover:bg-secondary disabled:opacity-50"
                  >
                    <div>
                      <div className="text-sm font-medium">{a.email}</div>
                      <div className="text-xs text-muted-foreground">пароль: {a.password}</div>
                    </div>
                    <RoleBadge role={a.role} />
                  </button>
                ))}
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
