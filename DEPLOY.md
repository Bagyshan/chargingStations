# Деплой на один сервер (Docker Compose)

Весь стек (инфраструктура + Consul + SteVe + 11 сервисов + nginx) поднимается одним файлом
`docker-compose.prod.yaml`. Единая точка входа — **nginx на :80**, который проксирует на
`api-gateway-service` (REST всех сервисов + WebSocket) и на Keycloak (его корневые пути `/realms`, `/admin`, `/resources`).

## Требования
- Docker + Docker Compose plugin
- Java 21 на машине сборки (jar собираются на хосте; SteVe собирается внутри своего контейнера)

## Быстрый старт
```bash
cp .env.example .env          # заполнить SERVER_HOST и пароли
nano .env
./deploy.sh                   # собрать jar + поднять весь стек
```
`deploy.sh --no-build` — поднять стек без пересборки jar.

## Что нужно в .env
- `SERVER_HOST` — публичный IP (или домен) сервера. Используется в issuer токенов Keycloak,
  ссылках в письмах и callback-URL O!Dengi.
- Пароли всех БД (`*_DB_PASSWORD`) — **обязательно сменить** с `changeme`.
- `KEYCLOAK_*` — админ Keycloak и секрет клиента `user-service`.
- `EMAIL_USERNAME` / `EMAIL_PASSWORD` — Yandex SMTP.
- `DENGI_*` — реквизиты O!Dengi (по умолчанию sandbox; для прода заменить и `DENGI_TEST=0`).

Все хосты в `application.yaml` сервисов переопределяются переменными окружения прямо в compose
(Spring relaxed-binding), сами конфиги не меняются.

## Доступ после запуска
| Что | URL |
|---|---|
| API (через gateway) | `http://<SERVER_HOST>/<prefix>/...` напр. `/station-controll/api/...`, `/user/...`, `/booking/...` |
| Swagger (агрегатор) | `http://<SERVER_HOST>/swagger-ui/index.html` |
| WebSocket | `ws://<SERVER_HOST>/websocket/ws/station-events?token=<JWT>` |

### Админ-панели — всё через nginx :80 по путям (отдельные порты НЕ открываются)
| Панель | URL | Логин |
|---|---|---|
| Keycloak (админка) | `http://<SERVER_HOST>/admin/` | KEYCLOAK_ADMIN / KEYCLOAK_ADMIN_PASSWORD |
| Keycloak (realm/OIDC) | `http://<SERVER_HOST>/realms/charging-stations` | — |
| SteVe (OCPP менеджер) | `http://<SERVER_HOST>/steve/manager` | admin / 1234 (config/docker/main.properties) |
| Consul UI | `http://<SERVER_HOST>/ui/` | — |
| Kafka-UI | `http://<SERVER_HOST>/kafka/` | — |

> Всё проксируется через nginx (Keycloak — `/admin`,`/realms`,`/resources`,`/js`; SteVe — `/steve/`
> вкл. OCPP WebSocket; Consul — `/ui/`+`/v1/`; Kafka-UI — `/kafka/`). Static-ресурсы работают.
> Контейнерные порты наружу не публикуются (кроме nginx :80 и kafka :29092 для отладки).
>
> ⚠️ **Заходи по тому же хосту, что в `SERVER_HOST`** (напр. `http://localhost/admin/`, а не `http://0.0.0.0/`).
> Keycloak привязывает issuer и redirect_uri к `KC_HOSTNAME=SERVER_HOST`; при заходе с другого хоста
> админка отдаёт «Invalid parameter: redirect_uri».
>
> SteVe OCPP для зарядных станций: `ws://<SERVER_HOST>/steve/websocket/CentralSystemService/<chargeBoxId>`.

Префиксы gateway: `/station-integration`, `/station-controll`, `/state-updater`, `/websocket`,
`/contractor-admin`, `/user`, `/payment`, `/booking` (см. `api-gateway-service`).

## Проверка
```bash
docker compose -f docker-compose.prod.yaml ps          # все healthy/up
curl http://<SERVER_HOST>/realms/charging-stations/.well-known/openid-configuration
# топики созданы — Kafka-UI :8041; сервисы зарегистрированы — Consul :8500
```

## Важные замечания
- **Keycloak realm — единый для всех окружений.** Realm `charging-stations` хранится в репозитории:
  `keycloak-realm-config/charging-stations-realm.json` (полный экспорт боевого realm: роли
  USER/CONTRACTOR/SPECIALIST/ADMIN, клиент `user-service` с секретом, пользователи, SMTP). И prod,
  и `docker-compose.local.yaml` монтируют этот каталог и стартуют Keycloak с `--import-realm`, так что
  везде поднимается ИДЕНТИЧНЫЙ realm.
  - `--import-realm` импортирует realm **только если его ещё нет** в БД Keycloak. Чтобы накатить
    обновлённый realm на уже существующую БД — пересоздать том: `docker compose -f docker-compose.prod.yaml
    rm -sf keycloak && docker volume rm chargingstations_keycloak-db-data` (СОТРЁТ данные Keycloak), затем `up`.
  - Чтобы обновить эталон после изменений в локальном Keycloak — переэкспортировать:
    `docker exec keycloak /opt/keycloak/bin/kc.sh export --realm charging-stations --users realm_file --file /tmp/r.json`
    затем `docker cp keycloak:/tmp/r.json keycloak-realm-config/charging-stations-realm.json`.
  - `KEYCLOAK_CLIENT_SECRET` в `.env` должен совпадать с секретом клиента `user-service` в realm-файле
    (сейчас совпадает). Если используешь браузерные OIDC-redirect потоки — добавь прод-URL в
    `redirectUris`/`webOrigins` клиента (сейчас там только `http://localhost:8005/*`).
  - ⚠️ Файл realm содержит секрет клиента, хэши паролей пользователей и SMTP — он в git намеренно
    (чтобы realm был одинаковым везде), но обращайся с ним как с чувствительным.
- **HTTPS / 443.** Сейчас только :80 (доступ по IP). Чтобы включить TLS:
  1. раскомментировать `443:443` и монтирование `./nginx/certs` в `docker-compose.prod.yaml`;
  2. положить `fullchain.pem` / `privkey.pem` в `./nginx/certs` (или выпустить через certbot);
  3. раскомментировать `server { listen 443 ssl; }` в `nginx/conf.d/default.conf`;
  4. сменить `SERVER_HOST` на домен и заменить `http://` на `https://` в issuer/ссылках.
- **Прод-режим Keycloak.** Для ужесточения заменить `start-dev` на `start` + `KC_HEALTH_ENABLED=true`
  и корректный `KC_HOSTNAME`/TLS.
- **O!Dengi callback** (`DENGI_RESULT_URL`) должен быть доступен из интернета
  (`http://<SERVER_HOST>/payment/api/v1/payments/callback`).

## Полезные команды
```bash
docker compose -f docker-compose.prod.yaml logs -f <service>
docker compose -f docker-compose.prod.yaml restart <service>
docker compose -f docker-compose.prod.yaml down            # остановить (данные в volume сохранятся)
docker compose -f docker-compose.prod.yaml down -v         # + удалить тома (СОТРЁТ БД)
```

> `docker-compose.local.yaml` остаётся для локальной разработки — он поднимает только инфраструктуру,
> сервисы запускаются из IDE. Для полноценного деплоя используется `docker-compose.prod.yaml`.
