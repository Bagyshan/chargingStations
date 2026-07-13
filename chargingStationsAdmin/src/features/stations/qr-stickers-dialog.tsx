import { Download, Info, Printer, QrCode } from 'lucide-react';
import { Dialog } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { EmptyState } from '@/components/empty-state';
import { API_BASE } from '@/api/config';
import { USE_MOCK } from '@/api/client';
import type { ChargeBox, Connector } from '@/types/domain';

/**
 * QR-наклейки коннекторов станции. Бэкенд station-controll генерирует их публично:
 *  - PNG одного коннектора: GET /station-controll/api/stations/{cb}/qr/{connectorId}
 *  - печатный лист со всеми: GET /station-controll/api/stations/{cb}/qr-sheet
 * QR несёт deep-link batenergy://charge?station=&connector= — скан открывает зарядку.
 */
export function QrStickersDialog({
  open,
  onClose,
  station,
  connectors,
}: {
  open: boolean;
  onClose: () => void;
  station: ChargeBox;
  connectors: Connector[];
}) {
  const sheetUrl = `${API_BASE}/station-controll/api/stations/${station.chargeBoxId}/qr-sheet`;
  const pngUrl = (connectorId: number) =>
    `${API_BASE}/station-controll/api/stations/${station.chargeBoxId}/qr/${connectorId}?size=260`;

  return (
    <Dialog
      open={open}
      onClose={onClose}
      size="lg"
      title="QR-коды коннекторов"
      description={station.addressName ?? station.chargeBoxId}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Закрыть
          </Button>
          {!USE_MOCK && connectors.length > 0 && (
            <Button variant="accent" onClick={() => window.open(sheetUrl, '_blank', 'noopener')}>
              <Printer className="size-4" />
              Печать наклеек
            </Button>
          )}
        </>
      }
    >
      {USE_MOCK ? (
        <div className="flex items-start gap-2 rounded-lg border border-border p-4 text-sm text-muted-foreground">
          <Info className="mt-0.5 size-4 shrink-0 text-info" />
          QR-коды генерируются на сервере station-controll. Они доступны при подключении к реальному
          API (VITE_DATA_SOURCE=api).
        </div>
      ) : connectors.length === 0 ? (
        <EmptyState icon={QrCode} title="Нет коннекторов" message="Для станции пока нет коннекторов" />
      ) : (
        <>
          <p className="mb-4 text-sm text-muted-foreground">
            Наклейка клеится на каждый коннектор. Скан в приложении BatEnergy сразу открывает зарядку
            нужного коннектора.
          </p>
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
            {connectors.map((c) => (
              <div key={c.id} className="rounded-xl border border-border p-3 text-center">
                <img
                  src={pngUrl(c.connectorId)}
                  alt={`QR коннектора ${c.connectorId}`}
                  className="mx-auto aspect-square w-full max-w-[180px] rounded-lg bg-white p-2"
                  loading="lazy"
                />
                <div className="mt-2 text-sm font-semibold">Коннектор №{c.connectorId}</div>
                <div className="text-xs text-muted-foreground">{c.connectorTypeName ?? '—'}</div>
                <a
                  href={pngUrl(c.connectorId)}
                  download={`${station.chargeBoxId}-connector-${c.connectorId}.png`}
                  className="mt-1.5 inline-flex items-center gap-1 text-xs font-medium text-primary hover:underline"
                >
                  <Download className="size-3.5" />
                  PNG
                </a>
              </div>
            ))}
          </div>
        </>
      )}
    </Dialog>
  );
}
