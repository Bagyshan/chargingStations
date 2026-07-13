import { Link } from '@tanstack/react-router';
import { X } from 'lucide-react';
import { Logo } from './logo';
import { visibleNav } from './nav-config';
import { useAuth } from '@/store/auth';
import { cn } from '@/lib/utils';

export function Sidebar({ mobileOpen, onClose }: { mobileOpen: boolean; onClose: () => void }) {
  const role = useAuth((s) => s.account?.role ?? 'USER');
  const groups = visibleNav(role);

  return (
    <>
      {/* Затемнение для мобильного оверлея */}
      {mobileOpen && (
        <div
          className="fixed inset-0 z-40 bg-black/50 backdrop-blur-sm lg:hidden"
          onClick={onClose}
        />
      )}
      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-50 flex w-64 flex-col border-r border-border bg-sidebar text-sidebar-foreground transition-transform lg:static lg:translate-x-0',
          mobileOpen ? 'translate-x-0' : '-translate-x-full',
        )}
      >
        <div className="flex h-16 items-center justify-between px-4">
          <Logo />
          <button className="lg:hidden text-muted-foreground" onClick={onClose}>
            <X className="size-5" />
          </button>
        </div>

        <nav className="flex-1 space-y-6 overflow-y-auto scrollbar-thin px-3 py-4">
          {groups.map((group) => (
            <div key={group.title}>
              <div className="mb-1.5 px-3 text-[11px] font-semibold uppercase tracking-wider text-muted-foreground/70">
                {group.title}
              </div>
              <div className="space-y-0.5">
                {group.items.map((item) => (
                  <Link
                    key={item.to}
                    to={item.to}
                    onClick={onClose}
                    activeOptions={{ exact: item.to === '/' }}
                    className="group flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground data-[status=active]:bg-primary/12 data-[status=active]:text-primary"
                  >
                    <item.icon className="size-4.5 shrink-0" />
                    {item.label}
                  </Link>
                ))}
              </div>
            </div>
          ))}
        </nav>

        <div className="border-t border-border p-3">
          <div className="rounded-lg bg-muted/50 p-3 text-xs text-muted-foreground">
            <span className="font-semibold text-foreground">Демо-режим.</span> Данные из мок-слоя. В
            Фазе 2 подключается реальный API-шлюз.
          </div>
        </div>
      </aside>
    </>
  );
}
