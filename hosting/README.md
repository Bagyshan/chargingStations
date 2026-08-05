# hosting/ — публичная статика приложения (раздаётся nginx)

Файлы, которые пользовательское приложение и сторы берут по HTTPS с
`bat-energy.com.kg`. Лежат **в монорепозитории** и раздаются nginx напрямую
(bind-mount, без прокси к сервисам), поэтому правятся **без пересборки приложения
и без ручного копирования на сервер**.

| Файл | URL | Зачем |
|---|---|---|
| `app-config.json` | `https://bat-energy.com.kg/app-config.json` | Контакты (WhatsApp/Telegram/телефон/почта), часы работы, ссылки на документы, **FAQ**. Приложение тянет при запуске; при недоступности — вшитые дефолты. |
| `privacy.html` | `https://bat-energy.com.kg/privacy` | Политика конфиденциальности (нужен публичный URL для App Store / Google Play). |
| `terms.html` | `https://bat-energy.com.kg/terms` | Условия использования. |

## Как обновить (обычный процесс — без ручной возни)

1. Меняешь нужный файл **локально** в `hosting/`.
2. `git commit && git push`.
3. На сервере: `git pull`.
4. **Готово.** nginx раздаёт файл из bind-mount `./hosting` — новый контент уже
   отдаётся. Reload/restart nginx **не нужен** (это статика).

> Приложение подхватит новый `app-config.json` при следующем запуске (есть
> клиентский кэш ~5 мин из-за заголовка `Cache-Control: max-age=300`).

### Первый раз (разово, чтобы контейнер увидел новый том/локейшены)
Том `./hosting` и nginx-локейшены (`/app-config.json`, `/privacy`, `/terms`) уже
прописаны в `docker-compose.prod.yaml` и `nginx/conf.d/default.conf`. При первом
деплое этих изменений пересоздай nginx, чтобы примонтировался том:

```bash
docker compose -f docker-compose.prod.yaml up -d nginx
```

Дальше — только `git pull`, как выше.

## Что редактировать в app-config.json

- `support.telegram` — ссылка на Telegram-бота поддержки (`https://t.me/<бот>`),
  см. `support-bot-service/README.md`.
- `support.whatsapp` / `support.phone` / `support.email` / `support.hours`.
- `legal.privacyUrl` / `legal.termsUrl` — обычно менять не нужно.
- `faq` — массив `{ "q": "вопрос", "a": "ответ" }`, добавляй/убирай свободно.

## Universal/App Links (`.well-known/`)

Чтобы ссылка подтверждения email из письма **открывала приложение**, на домене
должны раздаваться association-файлы (nginx уже настроен, отдаёт `application/json`):

| Файл | URL | Для чего |
|---|---|---|
| `.well-known/apple-app-site-association` | `https://bat-energy.com.kg/.well-known/apple-app-site-association` | iOS Universal Links. Внутри `appID = LB4B33JAQ7.com.batenergy.app` и путь `/user/api/v1/auth/verify-email*`. |
| `.well-known/assetlinks.json` | `https://bat-energy.com.kg/.well-known/assetlinks.json` | Android App Links. **Плейсхолдер** `REPLACE_WITH_ANDROID_RELEASE_SHA256` — заменить на SHA-256 релизного ключа, когда пойдём в Google Play (для iOS не нужно). |

Правятся так же: изменил → `git push` → `git pull` на сервере. iOS кэширует AASA —
после изменения переустанови приложение (или подожди), чтобы система перечитала файл.

## Проверка

```bash
curl -s https://bat-energy.com.kg/app-config.json | head
curl -sI https://bat-energy.com.kg/privacy
# association-файлы должны отдаваться как application/json:
curl -sI https://bat-energy.com.kg/.well-known/apple-app-site-association
```
