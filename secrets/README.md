# Секреты (не коммитить!)

Сюда кладётся ключ Firebase для push-уведомлений:

- `firebase-service-account.json` — service-account ключ из Firebase Console
  (Project settings → Service accounts → Generate new private key).
  Монтируется в notification-service (`docker-compose.prod.yaml`).
  Пока файла нет — notification-service работает, пуши выключены (WARN в логе).

Добавьте каталог в `.gitignore`, если репозиторий станет публичным.
