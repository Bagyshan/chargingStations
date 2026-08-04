# support-bot-service

Telegram-бот поддержки BatEnergy. Пользователь пишет боту → описывает проблему →
оставляет контакт → жалоба **форвардится в чат/группу поддержки** и **сохраняется в
Postgres** (`supportbotdb.support_complaint`).

- **Порт:** 8014 (actuator/health; сам бот работает через long polling к Telegram, входящих HTTP-запросов от клиентов нет).
- **Подключение к Telegram:** long polling (ничего публично открывать не нужно, nginx не трогаем).
- **БД:** `supportbotdb` (Postgres 16), контейнер `support-bot-service-db`. Миграции — Liquibase.

## Диалог бота

```
/start   → «Опишите проблему одним сообщением»
<текст>  → «Оставьте контакт (телефон/email) или /skip»
<текст>  → жалоба сохранена + форвард в поддержку + «Спасибо, обращение принято»
/cancel  → отменить обращение
```

Telegram-имя, username и id пользователя подхватываются автоматически.

## Настройка (что нужно сделать один раз)

### 1. Создать бота и получить токен
1. В Telegram открой **@BotFather** → `/newbot` → задай имя и username (например `batenergy_support_bot`).
2. BotFather выдаст **токен** вида `1234567890:AAE...`. Впиши его в `.env`:
   ```
   TELEGRAM_BOT_TOKEN=1234567890:AAE...
   ```

### 2. Куда бот шлёт жалобы — узнать chat id поддержки
Вариант A — **личка** (жалобы приходят вам в ЛС):
- напиши своему боту любое сообщение, затем открой
  `https://api.telegram.org/bot<ТОКЕН>/getUpdates` — в ответе найди `chat.id` (положительное число).

Вариант B — **группа поддержки** (рекомендуется, отвечает вся команда):
1. Создай группу, добавь в неё бота.
2. Отправь в группу любое сообщение.
3. Открой `https://api.telegram.org/bot<ТОКЕН>/getUpdates` — возьми `chat.id`
   (для групп это **отрицательное** число, напр. `-1001234567890`).
4. Впиши в `.env`:
   ```
   TELEGRAM_SUPPORT_CHAT_ID=-1001234567890
   ```

> Если `TELEGRAM_SUPPORT_CHAT_ID` оставить пустым — жалобы всё равно сохраняются в БД,
> просто без мгновенного форварда в Telegram.

### 3. Дать боту ссылку в приложении
После запуска бота впиши ссылку на него в `hosting/app-config.json`:
```json
"support": { "telegram": "https://t.me/batenergy_support_bot", ... }
```
и залей файл на `bat-energy.com.kg/app-config.json` — приложение сразу начнёт
открывать бота по кнопке «Telegram» в разделе «Поддержка и связь» (без пересборки апки).

## Переменные окружения (docker-compose.prod.yaml)

| Переменная | Назначение |
|---|---|
| `TELEGRAM_BOT_TOKEN` | Токен бота из @BotFather (**обязателен**). |
| `TELEGRAM_SUPPORT_CHAT_ID` | Куда форвардить жалобы (опционально). |
| `SUPPORT_BOT_DB_PASSWORD` | Пароль Postgres `supportbotdb`. |

## Запуск

Сборка и деплой — как у остальных сервисов (`deploy.sh` / `docker compose -f docker-compose.prod.yaml up -d --build support-bot-service support-bot-service-db`).

Локально:
```bash
mvn -pl support-bot-service clean package -DskipTests
TELEGRAM_BOT_TOKEN=... TELEGRAM_SUPPORT_CHAT_ID=... \
  java -jar support-bot-service/target/support-bot-service-1.0-SNAPSHOT-exec.jar
```
(нужен доступный Postgres `supportbotdb` — параметры в `application.yaml` / env `SPRING_R2DBC_*`).
