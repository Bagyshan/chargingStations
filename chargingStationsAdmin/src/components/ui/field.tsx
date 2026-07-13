import type { ReactNode } from 'react';
import { cn } from '@/lib/utils';

/** Обёртка поля формы: подпись, содержимое, подсказка/ошибка. */
export function Field({
  label,
  error,
  required,
  hint,
  children,
  className,
}: {
  label: string;
  error?: string;
  required?: boolean;
  hint?: string;
  children: ReactNode;
  className?: string;
}) {
  return (
    <div className={cn('space-y-1.5', className)}>
      <label className="flex items-center gap-1 text-sm font-medium">
        {label}
        {required && <span className="text-danger">*</span>}
      </label>
      {children}
      {error ? (
        <p className="text-xs text-danger">{error}</p>
      ) : hint ? (
        <p className="text-xs text-muted-foreground">{hint}</p>
      ) : null}
    </div>
  );
}
