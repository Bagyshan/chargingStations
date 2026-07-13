import type { LucideIcon } from 'lucide-react';
import { TrendingDown, TrendingUp } from 'lucide-react';
import { Card } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { cn } from '@/lib/utils';

export function StatCard({
  label,
  value,
  icon: Icon,
  delta,
  hint,
  tone = 'primary',
  loading,
}: {
  label: string;
  value: string;
  icon: LucideIcon;
  delta?: number;
  hint?: string;
  tone?: 'primary' | 'accent' | 'success' | 'info';
  loading?: boolean;
}) {
  const toneBg: Record<string, string> = {
    primary: 'bg-primary/12 text-primary',
    accent: 'bg-accent/15 text-accent',
    success: 'bg-success/15 text-success',
    info: 'bg-info/15 text-info',
  };
  return (
    <Card className="p-5">
      <div className="flex items-start justify-between">
        <span className="text-sm text-muted-foreground">{label}</span>
        <span className={cn('flex size-9 items-center justify-center rounded-lg', toneBg[tone])}>
          <Icon className="size-4.5" />
        </span>
      </div>
      {loading ? (
        <Skeleton className="mt-3 h-8 w-28" />
      ) : (
        <div className="mt-2 text-2xl font-bold tracking-tight">{value}</div>
      )}
      <div className="mt-1.5 flex items-center gap-2 text-xs">
        {delta != null && (
          <span
            className={cn(
              'inline-flex items-center gap-0.5 font-semibold',
              delta >= 0 ? 'text-success' : 'text-danger',
            )}
          >
            {delta >= 0 ? <TrendingUp className="size-3.5" /> : <TrendingDown className="size-3.5" />}
            {Math.abs(delta).toFixed(1)}%
          </span>
        )}
        {hint && <span className="text-muted-foreground">{hint}</span>}
      </div>
    </Card>
  );
}
