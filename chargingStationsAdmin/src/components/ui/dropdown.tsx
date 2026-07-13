import { useRef, useState, type ReactNode } from 'react';
import { cn } from '@/lib/utils';
import { useClickOutside } from './use-click-outside';

interface DropdownProps {
  trigger: ReactNode;
  children: ReactNode | ((close: () => void) => ReactNode);
  align?: 'start' | 'end';
  className?: string;
  contentClassName?: string;
}

/** Лёгкое меню без внешних зависимостей: click-outside + Esc. */
export function Dropdown({ trigger, children, align = 'end', className, contentClassName }: DropdownProps) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  useClickOutside(ref, () => setOpen(false));
  const close = () => setOpen(false);

  return (
    <div className={cn('relative', className)} ref={ref}>
      <div onClick={() => setOpen((v) => !v)}>{trigger}</div>
      {open && (
        <div
          className={cn(
            'absolute z-50 mt-2 min-w-48 origin-top rounded-lg border border-border bg-popover p-1 text-popover-foreground shadow-xl animate-in-up',
            align === 'end' ? 'right-0' : 'left-0',
            contentClassName,
          )}
        >
          {typeof children === 'function' ? children(close) : children}
        </div>
      )}
    </div>
  );
}

export function DropdownItem({
  className,
  icon,
  danger,
  ...props
}: React.ButtonHTMLAttributes<HTMLButtonElement> & { icon?: ReactNode; danger?: boolean }) {
  return (
    <button
      className={cn(
        'flex w-full items-center gap-2.5 rounded-md px-2.5 py-2 text-sm transition-colors',
        'hover:bg-secondary [&_svg]:size-4 [&_svg]:text-muted-foreground',
        danger && 'text-danger hover:bg-danger/10 [&_svg]:text-danger',
        className,
      )}
      {...props}
    >
      {icon}
      {props.children}
    </button>
  );
}

export function DropdownLabel({ children }: { children: ReactNode }) {
  return <div className="px-2.5 py-1.5 text-xs font-semibold text-muted-foreground">{children}</div>;
}

export function DropdownSeparator() {
  return <div className="my-1 h-px bg-border" />;
}
