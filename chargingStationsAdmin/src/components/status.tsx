import { Badge, type BadgeProps } from '@/components/ui/badge';
import { cn } from '@/lib/utils';
import {
  ROLE_LABELS,
  type BookingStatus,
  type ConnectorStatus,
  type Role,
  type ServiceStatus,
  type TransactionStatus,
} from '@/types/domain';

type Meta = { label: string; variant: BadgeProps['variant'] };

const CONNECTOR: Record<ConnectorStatus, Meta> = {
  Available: { label: 'Свободен', variant: 'success' },
  Charging: { label: 'Заряжает', variant: 'info' },
  Preparing: { label: 'Подготовка', variant: 'warning' },
  Finishing: { label: 'Завершение', variant: 'warning' },
  Reserved: { label: 'Бронь', variant: 'primary' },
  Unavailable: { label: 'Недоступен', variant: 'default' },
  Faulted: { label: 'Ошибка', variant: 'danger' },
};

const SERVICE: Record<ServiceStatus, Meta> = {
  IN_SERVICE: { label: 'В работе', variant: 'success' },
  MAINTENANCE: { label: 'Обслуживание', variant: 'warning' },
  OUT_OF_SERVICE: { label: 'Выведена', variant: 'danger' },
};

const TRANSACTION: Record<TransactionStatus, Meta> = {
  ACTIVE: { label: 'Активна', variant: 'info' },
  COMPLETED: { label: 'Завершена', variant: 'success' },
  CANCELLED: { label: 'Отменена', variant: 'default' },
  REJECTED: { label: 'Отклонена', variant: 'danger' },
};

const BOOKING: Record<BookingStatus, Meta> = {
  ACTIVE: { label: 'Активна', variant: 'info' },
  START_RESERVATION: { label: 'Активна', variant: 'info' },
  COMPLETED: { label: 'Завершена', variant: 'success' },
  STOP_RESERVATION: { label: 'Завершена', variant: 'success' },
  CANCELLED: { label: 'Отменена', variant: 'default' },
  REJECTED: { label: 'Отклонена', variant: 'danger' },
  FAILED: { label: 'Не удалась', variant: 'danger' },
};

const ROLE: Record<Role, BadgeProps['variant']> = {
  ADMIN: 'primary',
  SPECIALIST: 'info',
  CONTRACTOR: 'warning',
  USER: 'default',
};

export function ConnectorStatusBadge({ status }: { status: ConnectorStatus }) {
  const m = CONNECTOR[status];
  return <Badge variant={m.variant}>{m.label}</Badge>;
}

export function ServiceStatusBadge({ status }: { status?: ServiceStatus }) {
  if (!status) return null;
  const m = SERVICE[status];
  return <Badge variant={m.variant}>{m.label}</Badge>;
}

export function TransactionStatusBadge({ status }: { status: TransactionStatus }) {
  const m = TRANSACTION[status];
  return <Badge variant={m.variant}>{m.label}</Badge>;
}

export function BookingStatusBadge({ status }: { status: BookingStatus }) {
  const m = BOOKING[status];
  return <Badge variant={m.variant}>{m.label}</Badge>;
}

export function RoleBadge({ role }: { role: Role }) {
  return <Badge variant={ROLE[role]}>{ROLE_LABELS[role]}</Badge>;
}

/** Индикатор онлайн/оффлайн станции. */
export function OnlineDot({ online }: { online?: boolean }) {
  return (
    <span className="inline-flex items-center gap-1.5 text-sm">
      <span
        className={cn(
          'size-2 rounded-full',
          online ? 'bg-success shadow-[0_0_0_3px] shadow-success/20' : 'bg-muted-foreground/40',
        )}
      />
      <span className={online ? 'text-foreground' : 'text-muted-foreground'}>
        {online ? 'Онлайн' : 'Оффлайн'}
      </span>
    </span>
  );
}

/** Цвет статуса коннектора для точек/иконок. */
export const connectorStatusColor: Record<ConnectorStatus, string> = {
  Available: 'var(--color-success)',
  Charging: 'var(--color-info)',
  Preparing: 'var(--color-warning)',
  Finishing: 'var(--color-warning)',
  Reserved: 'var(--color-primary)',
  Unavailable: 'var(--color-muted-foreground)',
  Faulted: 'var(--color-danger)',
};
