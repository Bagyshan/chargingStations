/**
 * Полифиллы для небезопасного контекста (http://<ip> без HTTPS).
 *
 * `crypto.randomUUID()` доступен ТОЛЬКО в secure context (HTTPS или localhost),
 * поэтому на проде по http://<SERVER_HOST> он undefined и падает с
 * «crypto.randomUUID is not a function» → приложение не монтируется (чёрный экран).
 * `crypto.getRandomValues()` в небезопасном контексте доступен — строим UUID v4 на нём.
 *
 * ВАЖНО: этот модуль должен импортироваться ПЕРВЫМ в main.tsx, до любого кода,
 * который может дёрнуть crypto.randomUUID на этапе инициализации модулей (напр. mock-сид).
 */
if (typeof crypto !== 'undefined' && typeof crypto.randomUUID !== 'function') {
  const uuid = (): `${string}-${string}-${string}-${string}-${string}` => {
    const b = crypto.getRandomValues(new Uint8Array(16));
    b[6] = (b[6] & 0x0f) | 0x40; // версия 4
    b[8] = (b[8] & 0x3f) | 0x80; // вариант 10xx
    const h = Array.from(b, (x) => x.toString(16).padStart(2, '0'));
    return `${h[0]}${h[1]}${h[2]}${h[3]}-${h[4]}${h[5]}-${h[6]}${h[7]}-${h[8]}${h[9]}-${h[10]}${h[11]}${h[12]}${h[13]}${h[14]}${h[15]}`;
  };
  // getRandomValues есть и в небезопасном контексте; на всякий случай — фолбэк на Math.random.
  (crypto as { randomUUID: () => string }).randomUUID =
    typeof crypto.getRandomValues === 'function'
      ? uuid
      : () =>
          'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
            const r = (Math.random() * 16) | 0;
            return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
          });
}
