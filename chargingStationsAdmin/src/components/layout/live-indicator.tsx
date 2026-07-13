import { useRealtimeStore } from '@/store/realtime';
import { cn } from '@/lib/utils';

/** Индикатор realtime-канала: зелёный пульс при активном соединении. */
export function LiveIndicator() {
  const status = useRealtimeStore((s) => s.status);
  if (status === 'off') return null;
  const live = status === 'live';
  return (
    <div
      className={cn(
        'hidden items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-medium sm:flex',
        live ? 'border-success/30 text-success' : 'border-border text-muted-foreground',
      )}
      title={live ? 'Realtime активен' : 'Подключение к realtime…'}
    >
      <span className="relative flex size-2">
        {live && (
          <span className="absolute inline-flex size-full animate-ping rounded-full bg-success/60" />
        )}
        <span
          className={cn('relative inline-flex size-2 rounded-full', live ? 'bg-success' : 'bg-muted-foreground')}
        />
      </span>
      {live ? 'Live' : '…'}
    </div>
  );
}
