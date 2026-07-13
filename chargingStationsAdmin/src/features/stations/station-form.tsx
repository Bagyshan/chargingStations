import { useEffect, useState, type FormEvent } from 'react';
import { Loader2 } from 'lucide-react';
import { Dialog } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Field } from '@/components/ui/field';
import { useAddresses, useCreateStation, useOwners, useUpdateStation } from '@/api/hooks';
import { useAuth } from '@/store/auth';
import { toast } from '@/store/toast';
import type { ChargeBox } from '@/types/domain';

interface FormState {
  chargeBoxId: string;
  ocppProtocol: string;
  chargePointVendor: string;
  chargePointModel: string;
  ocppTag: string;
  ownerId: string;
  power: string;
  kwCost: string;
  bookingMinuteCost: string;
  addressId: string;
  latitude: string;
  longitude: string;
  firmwareVersion: string;
}

const empty: FormState = {
  chargeBoxId: '',
  ocppProtocol: 'ocpp1.6',
  chargePointVendor: '',
  chargePointModel: '',
  ocppTag: '',
  ownerId: '',
  power: '',
  kwCost: '',
  bookingMinuteCost: '',
  addressId: '',
  latitude: '',
  longitude: '',
  firmwareVersion: '',
};

function fromStation(s: ChargeBox): FormState {
  return {
    chargeBoxId: s.chargeBoxId,
    ocppProtocol: s.ocppProtocol ?? 'ocpp1.6',
    chargePointVendor: s.chargePointVendor ?? '',
    chargePointModel: s.chargePointModel ?? '',
    ocppTag: s.ocppTag ?? '',
    ownerId: s.ownerId ?? '',
    power: s.power ?? '',
    kwCost: s.kwCost != null ? String(s.kwCost) : '',
    bookingMinuteCost: s.bookingMinuteCost != null ? String(s.bookingMinuteCost) : '',
    addressId: s.addressId != null ? String(s.addressId) : '',
    latitude: s.latitude != null ? String(s.latitude) : '',
    longitude: s.longitude != null ? String(s.longitude) : '',
    firmwareVersion: s.firmwareVersion ?? '',
  };
}

export function StationForm({
  open,
  onClose,
  station,
}: {
  open: boolean;
  onClose: () => void;
  station?: ChargeBox;
}) {
  const role = useAuth((s) => s.account?.role ?? 'USER');
  const isEdit = !!station;
  const addresses = useAddresses();
  const owners = useOwners();
  const create = useCreateStation();
  const update = useUpdateStation();

  const [form, setForm] = useState<FormState>(empty);
  const [errors, setErrors] = useState<Partial<Record<keyof FormState, string>>>({});
  const busy = create.isPending || update.isPending;

  useEffect(() => {
    if (open) {
      setForm(station ? fromStation(station) : empty);
      setErrors({});
    }
  }, [open, station]);

  const set = (k: keyof FormState, v: string) => setForm((f) => ({ ...f, [k]: v }));

  function validate(): boolean {
    const e: Partial<Record<keyof FormState, string>> = {};
    if (!form.chargeBoxId.trim()) e.chargeBoxId = 'Укажите идентификатор станции';
    if (!form.power.trim()) e.power = 'Укажите мощность';
    if (form.kwCost && Number(form.kwCost) < 0) e.kwCost = 'Не может быть отрицательным';
    setErrors(e);
    return Object.keys(e).length === 0;
  }

  function submit(ev: FormEvent) {
    ev.preventDefault();
    if (!validate()) return;

    const data: Partial<ChargeBox> = {
      chargeBoxId: form.chargeBoxId.trim(),
      ocppProtocol: form.ocppProtocol || null,
      chargePointVendor: form.chargePointVendor || null,
      chargePointModel: form.chargePointModel || null,
      ocppTag: form.ocppTag || null,
      power: form.power.trim(),
      kwCost: form.kwCost ? Number(form.kwCost) : null,
      bookingMinuteCost: form.bookingMinuteCost ? Number(form.bookingMinuteCost) : null,
      addressId: form.addressId ? Number(form.addressId) : null,
      firmwareVersion: form.firmwareVersion || null,
      latitude: form.latitude ? Number(form.latitude) : undefined,
      longitude: form.longitude ? Number(form.longitude) : undefined,
    };
    if (role !== 'CONTRACTOR') data.ownerId = form.ownerId || null;

    const onDone = {
      onSuccess: () => {
        toast.success(isEdit ? 'Станция обновлена' : 'Станция создана', form.chargeBoxId);
        onClose();
      },
      onError: (e: Error) => toast.error('Не удалось сохранить', e.message),
    };

    if (station) update.mutate({ id: station.id, data }, onDone);
    else create.mutate(data, onDone);
  }

  return (
    <Dialog
      open={open}
      onClose={onClose}
      size="lg"
      title={isEdit ? 'Редактировать станцию' : 'Новая станция'}
      description={isEdit ? station!.chargeBoxId : 'Заполните параметры зарядной станции'}
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={busy}>
            Отмена
          </Button>
          <Button variant="accent" form="station-form" type="submit" disabled={busy}>
            {busy && <Loader2 className="size-4 animate-spin" />}
            {isEdit ? 'Сохранить' : 'Создать'}
          </Button>
        </>
      }
    >
      <form id="station-form" onSubmit={submit} className="grid gap-4 sm:grid-cols-2">
        <Field label="ID станции (OCPP)" required error={errors.chargeBoxId}>
          <Input
            value={form.chargeBoxId}
            onChange={(e) => set('chargeBoxId', e.target.value)}
            placeholder="CP_001"
            disabled={isEdit}
          />
        </Field>
        <Field label="Мощность" required error={errors.power} hint="например, 60 или 120">
          <Input value={form.power} onChange={(e) => set('power', e.target.value)} placeholder="60" />
        </Field>

        <Field label="Адрес">
          <Select value={form.addressId} onChange={(e) => set('addressId', e.target.value)}>
            <option value="">— не выбран —</option>
            {addresses.data?.map((a) => (
              <option key={a.id} value={a.id}>
                {a.addressName}
              </option>
            ))}
          </Select>
        </Field>

        {role !== 'CONTRACTOR' && (
          <Field label="Владелец">
            <Select value={form.ownerId} onChange={(e) => set('ownerId', e.target.value)}>
              <option value="">— не задан —</option>
              {owners.data?.map((o) => (
                <option key={o.ownerId} value={o.ownerId}>
                  {o.name}
                </option>
              ))}
            </Select>
          </Field>
        )}

        <Field label="Тариф зарядки, сом/кВт·ч" error={errors.kwCost}>
          <Input
            type="number"
            step="0.5"
            min="0"
            value={form.kwCost}
            onChange={(e) => set('kwCost', e.target.value)}
            placeholder="14"
          />
        </Field>
        <Field label="Тариф брони, сом/мин">
          <Input
            type="number"
            step="0.5"
            min="0"
            value={form.bookingMinuteCost}
            onChange={(e) => set('bookingMinuteCost', e.target.value)}
            placeholder="0"
          />
        </Field>

        <Field label="OCPP протокол">
          <Select value={form.ocppProtocol} onChange={(e) => set('ocppProtocol', e.target.value)}>
            <option value="ocpp1.6">ocpp1.6</option>
            <option value="ocpp2.0.1">ocpp2.0.1</option>
          </Select>
        </Field>
        <Field label="OCPP тег (RFID)">
          <Input value={form.ocppTag} onChange={(e) => set('ocppTag', e.target.value)} placeholder="TAG_CP_001" />
        </Field>

        <Field label="Производитель">
          <Input value={form.chargePointVendor} onChange={(e) => set('chargePointVendor', e.target.value)} placeholder="ABB" />
        </Field>
        <Field label="Модель">
          <Input value={form.chargePointModel} onChange={(e) => set('chargePointModel', e.target.value)} placeholder="Terra 184" />
        </Field>

        <Field label="Широта">
          <Input value={form.latitude} onChange={(e) => set('latitude', e.target.value)} placeholder="42.8746" />
        </Field>
        <Field label="Долгота">
          <Input value={form.longitude} onChange={(e) => set('longitude', e.target.value)} placeholder="74.5698" />
        </Field>
      </form>
    </Dialog>
  );
}
