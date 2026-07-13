import { useEffect, useState, type FormEvent } from 'react';
import { Loader2 } from 'lucide-react';
import { Dialog } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Field } from '@/components/ui/field';
import { useConnectorTypes, useCreateConnector, useStations, useUpdateConnector } from '@/api/hooks';
import { toast } from '@/store/toast';
import type { Connector, ConnectorStatus } from '@/types/domain';

const STATUSES: ConnectorStatus[] = ['Available', 'Charging', 'Reserved', 'Unavailable', 'Faulted'];

export function ConnectorForm({
  open,
  onClose,
  connector,
  defaultChargeBoxId,
}: {
  open: boolean;
  onClose: () => void;
  connector?: Connector;
  defaultChargeBoxId?: string;
}) {
  const isEdit = !!connector;
  const stations = useStations();
  const types = useConnectorTypes();
  const create = useCreateConnector();
  const update = useUpdateConnector();
  const busy = create.isPending || update.isPending;

  const [chargeBoxId, setChargeBoxId] = useState('');
  const [connectorId, setConnectorId] = useState('1');
  const [connectorTypeId, setConnectorTypeId] = useState('');
  const [status, setStatus] = useState<ConnectorStatus>('Available');
  const [info, setInfo] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    if (!open) return;
    setError('');
    if (connector) {
      setChargeBoxId(connector.chargeBoxId);
      setConnectorId(String(connector.connectorId));
      setConnectorTypeId(connector.connectorTypeId != null ? String(connector.connectorTypeId) : '');
      setStatus(connector.status);
      setInfo(connector.info ?? '');
    } else {
      setChargeBoxId(defaultChargeBoxId ?? '');
      setConnectorId('1');
      setConnectorTypeId('');
      setStatus('Available');
      setInfo('');
    }
  }, [open, connector, defaultChargeBoxId]);

  function submit(ev: FormEvent) {
    ev.preventDefault();
    if (!chargeBoxId) return setError('Выберите станцию');
    if (!connectorId || Number(connectorId) < 1) return setError('Укажите номер коннектора');

    const data: Partial<Connector> = {
      chargeBoxId,
      connectorId: Number(connectorId),
      connectorTypeId: connectorTypeId ? Number(connectorTypeId) : null,
      status,
      info: info || null,
    };
    const onDone = {
      onSuccess: () => {
        toast.success(isEdit ? 'Коннектор обновлён' : 'Коннектор создан', `${chargeBoxId} · #${connectorId}`);
        onClose();
      },
      onError: (e: Error) => toast.error('Не удалось сохранить', e.message),
    };
    if (connector) update.mutate({ id: connector.id, data }, onDone);
    else create.mutate(data, onDone);
  }

  return (
    <Dialog
      open={open}
      onClose={onClose}
      title={isEdit ? 'Редактировать коннектор' : 'Новый коннектор'}
      description={isEdit ? `${connector!.chargeBoxId} · #${connector!.connectorId}` : undefined}
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={busy}>
            Отмена
          </Button>
          <Button variant="accent" form="connector-form" type="submit" disabled={busy}>
            {busy && <Loader2 className="size-4 animate-spin" />}
            {isEdit ? 'Сохранить' : 'Создать'}
          </Button>
        </>
      }
    >
      <form id="connector-form" onSubmit={submit} className="space-y-4">
        {error && <div className="rounded-md bg-danger/10 px-3 py-2 text-sm text-danger">{error}</div>}

        <Field label="Станция" required>
          <Select
            value={chargeBoxId}
            onChange={(e) => setChargeBoxId(e.target.value)}
            disabled={isEdit || !!defaultChargeBoxId}
          >
            <option value="">— выберите станцию —</option>
            {stations.data?.map((s) => (
              <option key={s.chargeBoxId} value={s.chargeBoxId}>
                {s.addressName ?? s.chargeBoxId} · {s.chargeBoxId}
              </option>
            ))}
          </Select>
        </Field>

        <div className="grid grid-cols-2 gap-4">
          <Field label="Номер коннектора" required>
            <Input
              type="number"
              min="1"
              value={connectorId}
              onChange={(e) => setConnectorId(e.target.value)}
              disabled={isEdit}
            />
          </Field>
          <Field label="Статус">
            <Select value={status} onChange={(e) => setStatus(e.target.value as ConnectorStatus)}>
              {STATUSES.map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </Select>
          </Field>
        </div>

        <Field label="Тип коннектора">
          <Select value={connectorTypeId} onChange={(e) => setConnectorTypeId(e.target.value)}>
            <option value="">— не выбран —</option>
            {types.data?.map((t) => (
              <option key={t.id} value={t.id}>
                {t.connectorTypeName} {t.code ? `(${t.code})` : ''}
              </option>
            ))}
          </Select>
        </Field>

        <Field label="Описание">
          <Input value={info} onChange={(e) => setInfo(e.target.value)} placeholder="необязательно" />
        </Field>
      </form>
    </Dialog>
  );
}
