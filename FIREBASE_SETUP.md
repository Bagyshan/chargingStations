# Настройка Firebase (FCM push-уведомления)

Код (мобилка + бэкенд) уже готов и без ключей работает в режиме «пуши выключены».
Чтобы включить пуши, нужно один раз создать бесплатный Firebase-проект (~10 минут).

## 1. Создать проект

1. Открой https://console.firebase.google.com → **Add project**.
2. Название, например `batenergy` (Google Analytics можно выключить) → **Create project**.

## 2. Добавить Android-приложение

1. В проекте: **Add app → Android**.
2. **Android package name**: `com.example.batenergy_app` (точно так).
3. **Register app** → скачай `google-services.json` (класть его в проект НЕ нужно —
   он нужен только как источник значений для шага 3).

## 3. Заполнить конфиг в мобильном приложении

Открой `chargingStationsMobile/batenergy_app/lib/core/config/firebase_config.dart`
и замени плейсхолдеры значениями из скачанного `google-services.json`:

| Поле в Dart          | Откуда в google-services.json                     |
|----------------------|---------------------------------------------------|
| `apiKey`             | `client[0].api_key[0].current_key`                |
| `appId`              | `client[0].client_info.mobilesdk_app_id`          |
| `messagingSenderId`  | `project_info.project_number`                     |
| `projectId`          | `project_info.project_id`                         |

Пересобери приложение: `flutter build apk --debug` (или release).

## 4. Ключ для бэкенда (отправка пушей)

1. Firebase Console → ⚙ **Project settings → Service accounts**.
2. **Generate new private key** → скачается JSON.
3. Положи файл на прод-сервер как `./secrets/firebase-service-account.json`
   (рядом с `docker-compose.prod.yaml` — он монтируется в notification-service).

## 5. Redeploy

На проде пересобрать/перезапустить: `notification-service`, `user-service`,
`payment-service`, `websocket-service` + прогнать `kafka-init` (новый топик
`payment.topup.events`):

```bash
docker compose -f docker-compose.prod.yaml up -d --build \
  kafka-init notification-service user-service payment-service websocket-service
```

## Проверка

- Лог notification-service при старте: `FCM initialized from ...`
  (если `FCM disabled: service-account file not found` — файл не подхватился).
- Залогинься в приложении → в логе user-service: `Device token registered`.
- Заверши зарядку/бронь или пополни кошелёк при свёрнутом приложении —
  придёт системный пуш.

## Какие пуши отправляются

| Событие | Пуш |
|---|---|
| Зарядка завершена (в т.ч. авто-стоп по лимиту) | «Зарядка завершена» + кВт·ч и сом |
| Станция ушла в Faulted во время зарядки | «Сбой зарядки» |
| Зарядка прервана (CANCELLED/REJECTED) | «Зарядка прервана» |
| Бронь: осталось ≤ 5 минут | «Бронь скоро истечёт» (один раз на бронь) |
| Бронь завершена / отменена / не хватило средств | соответствующий пуш |
| Пополнение кошелька зачислено (O!Dengi) | «Кошелёк пополнен» + сумма и баланс |

В форграунде приложение пуш не показывает — эти же события уже приходят по
WebSocket и отражаются в UI мгновенно.
