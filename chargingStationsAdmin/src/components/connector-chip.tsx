import { PlugZap } from 'lucide-react';
import { cn } from '@/lib/utils';
import { connectorStatusColor } from '@/components/status';
import type { ConnectorStatus } from '@/types/domain';

/**
 * Компактная плитка типа коннектора: разъём + код типа.
 * Цвет обводки задаётся статусом (если передан) — как в мобильном приложении.
 */
export function ConnectorChip({
  code,
  name,
  status,
  className,
}: {
  code?: string;
  name?: string;
  status?: ConnectorStatus;
  className?: string;
}) {
  const color = status ? connectorStatusColor[status] : 'var(--color-primary)';
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-md border px-2 py-1 text-xs font-medium',
        className,
      )}
      style={{ borderColor: `color-mix(in oklch, ${color} 45%, transparent)`, color }}
      title={name}
    >
      <PlugZap className="size-3.5" />
      {code ?? name ?? '—'}
    </span>
  );
}
