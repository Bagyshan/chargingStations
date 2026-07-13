import { useNavigate } from '@tanstack/react-router';
import { ChevronDown, LogOut, Menu, Moon, Repeat2, Sun, UserCog } from 'lucide-react';
import { Avatar } from '@/components/ui/avatar';
import { Button } from '@/components/ui/button';
import { Dropdown, DropdownItem, DropdownLabel, DropdownSeparator } from '@/components/ui/dropdown';
import { RoleBadge } from '@/components/status';
import { LiveIndicator } from './live-indicator';
import { useAuth } from '@/store/auth';
import { useTheme } from '@/store/theme';
import { ROLE_LABELS, type Role } from '@/types/domain';

const SWITCHABLE: Role[] = ['ADMIN', 'SPECIALIST', 'CONTRACTOR'];

export function Topbar({ onOpenSidebar }: { onOpenSidebar: () => void }) {
  const navigate = useNavigate();
  const account = useAuth((s) => s.account);
  const impersonate = useAuth((s) => s.impersonate);
  const canImpersonate = useAuth((s) => s.canImpersonate);
  const logout = useAuth((s) => s.logout);
  const { theme, toggle } = useTheme();

  const signOut = () => {
    logout();
    navigate({ to: '/login' });
  };

  const name = account
    ? `${account.firstName ?? ''} ${account.lastName ?? ''}`.trim() || account.email
    : '—';

  return (
    <header className="sticky top-0 z-30 flex h-16 items-center gap-3 border-b border-border bg-background/80 px-4 backdrop-blur-lg lg:px-6">
      <button
        className="flex size-9 items-center justify-center rounded-lg hover:bg-secondary lg:hidden"
        onClick={onOpenSidebar}
      >
        <Menu className="size-5" />
      </button>

      <div className="ml-auto flex items-center gap-2">
        <LiveIndicator />

        {/* Демо: переключатель роли (только в мок-режиме) */}
        {canImpersonate && (
          <Dropdown
            trigger={
              <Button variant="outline" size="sm" className="gap-1.5">
                <Repeat2 className="size-4 text-muted-foreground" />
                <span className="hidden sm:inline">Роль:</span>
                {account && <RoleBadge role={account.role} />}
                <ChevronDown className="size-3.5 text-muted-foreground" />
              </Button>
            }
          >
            {(close) => (
              <>
                <DropdownLabel>Демо · переключить роль</DropdownLabel>
                {SWITCHABLE.map((r) => (
                  <DropdownItem
                    key={r}
                    icon={<UserCog />}
                    onClick={() => {
                      impersonate(r);
                      close();
                    }}
                  >
                    {ROLE_LABELS[r]}
                  </DropdownItem>
                ))}
              </>
            )}
          </Dropdown>
        )}

        <Button variant="ghost" size="icon" onClick={toggle} title="Сменить тему">
          {theme === 'dark' ? <Sun className="size-4.5" /> : <Moon className="size-4.5" />}
        </Button>

        <Dropdown
          trigger={
            <button className="flex items-center gap-2 rounded-lg py-1 pl-1 pr-2 hover:bg-secondary">
              <Avatar name={name} size={32} />
              <div className="hidden text-left sm:block">
                <div className="text-sm font-semibold leading-tight">{name}</div>
                <div className="text-[11px] text-muted-foreground">{account?.email}</div>
              </div>
              <ChevronDown className="size-3.5 text-muted-foreground" />
            </button>
          }
        >
          <div className="flex items-center gap-3 px-2.5 py-2">
            <Avatar name={name} size={40} />
            <div>
              <div className="text-sm font-semibold">{name}</div>
              {account && <RoleBadge role={account.role} />}
            </div>
          </div>
          <DropdownSeparator />
          <DropdownItem icon={<LogOut />} danger onClick={signOut}>
            Выйти
          </DropdownItem>
        </Dropdown>
      </div>
    </header>
  );
}
