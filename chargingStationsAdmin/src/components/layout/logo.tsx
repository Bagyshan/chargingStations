import { cn } from '@/lib/utils';

export function Logo({ collapsed, className }: { collapsed?: boolean; className?: string }) {
  return (
    <div className={cn('flex items-center gap-2.5', className)}>
      <div className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-primary shadow-lg shadow-primary/30">
        <svg viewBox="0 0 32 32" className="size-5" aria-hidden>
          <path d="M17.5 5 9 18h5.2l-1.7 9 10.5-14h-6l0.5-8z" fill="#FFA20D" />
        </svg>
      </div>
      {!collapsed && (
        <div className="leading-tight">
          <div className="font-bold tracking-tight">BatEnergy</div>
          <div className="text-[11px] font-medium text-muted-foreground">Админ-панель</div>
        </div>
      )}
    </div>
  );
}
