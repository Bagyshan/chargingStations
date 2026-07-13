import type { ReactNode } from 'react';

/** Список «подпись → значение» для деталей сущностей. */
export function DetailList({ items }: { items: { label: string; value: ReactNode }[] }) {
  return (
    <div className="divide-y divide-border rounded-lg border border-border">
      {items.map((it, i) => (
        <div key={i} className="flex items-center justify-between gap-4 px-3 py-2.5 text-sm">
          <span className="shrink-0 text-muted-foreground">{it.label}</span>
          <span className="truncate text-right font-medium">
            {it.value === null || it.value === undefined || it.value === '' ? '—' : it.value}
          </span>
        </div>
      ))}
    </div>
  );
}
