import { AlertTriangle } from 'lucide-react';
import { Dialog } from './dialog';
import { Button } from './button';

/** Диалог подтверждения (например, удаление). */
export function ConfirmDialog({
  open,
  onClose,
  onConfirm,
  title,
  message,
  confirmLabel = 'Удалить',
  danger = true,
  loading,
}: {
  open: boolean;
  onClose: () => void;
  onConfirm: () => void;
  title: string;
  message: string;
  confirmLabel?: string;
  danger?: boolean;
  loading?: boolean;
}) {
  return (
    <Dialog
      open={open}
      onClose={onClose}
      title={title}
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={loading}>
            Отмена
          </Button>
          <Button variant={danger ? 'danger' : 'default'} onClick={onConfirm} disabled={loading}>
            {confirmLabel}
          </Button>
        </>
      }
    >
      <div className="flex gap-3">
        {danger && (
          <div className="flex size-10 shrink-0 items-center justify-center rounded-full bg-danger/12 text-danger">
            <AlertTriangle className="size-5" />
          </div>
        )}
        <p className="text-sm text-muted-foreground">{message}</p>
      </div>
    </Dialog>
  );
}
