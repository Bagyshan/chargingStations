import { cn } from '@/lib/utils';

/** Аватар с инициалами и детерминированным фирменным градиентом. */
export function Avatar({
  name,
  className,
  size = 36,
}: {
  name: string;
  className?: string;
  size?: number;
}) {
  const hue = hashHue(name);
  return (
    <div
      className={cn(
        'flex shrink-0 items-center justify-center rounded-full font-semibold text-white select-none',
        className,
      )}
      style={{
        width: size,
        height: size,
        fontSize: size * 0.4,
        background: `linear-gradient(135deg, oklch(0.62 0.19 ${hue}), oklch(0.7 0.16 ${(hue + 40) % 360}))`,
      }}
    >
      {name.trim().slice(0, 2).toUpperCase()}
    </div>
  );
}

function hashHue(s: string): number {
  let h = 0;
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) % 360;
  return h;
}
