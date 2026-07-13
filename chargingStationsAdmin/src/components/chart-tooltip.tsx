interface TooltipEntry {
  value?: number | string;
  name?: string;
  color?: string;
}

/** Единый стиль тултипа для всех графиков (без зависимости от типов recharts). */
export function ChartTooltip({
  active,
  payload,
  label,
  formatter,
  labelFormatter,
}: {
  active?: boolean;
  payload?: TooltipEntry[];
  label?: string | number;
  formatter?: (value: number, name: string) => string;
  labelFormatter?: (label: string) => string;
}) {
  if (!active || !payload?.length) return null;
  return (
    <div className="rounded-lg border border-border bg-popover/95 px-3 py-2 text-xs shadow-xl backdrop-blur">
      <div className="mb-1.5 font-semibold text-foreground">
        {labelFormatter ? labelFormatter(String(label)) : String(label)}
      </div>
      <div className="flex flex-col gap-1">
        {payload.map((p, i) => (
          <div key={i} className="flex items-center justify-between gap-4">
            <span className="flex items-center gap-1.5 text-muted-foreground">
              <span className="size-2 rounded-full" style={{ background: p.color }} />
              {p.name}
            </span>
            <span className="font-semibold text-foreground">
              {formatter ? formatter(Number(p.value), String(p.name)) : p.value}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}
