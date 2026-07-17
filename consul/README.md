# Consul KV — централизованный конфиг с hot-reload

Часть настроек вынесена из yaml в **Consul KV** и обновляется **на лету** (без пересборки/рестарта
сервисов). Работает через `spring-cloud-starter-consul-config`: сервисы читают ключи из KV на старте,
а `watch` (опрос раз в ~1 c) авто-рефрешит бины при изменении значения.

## Какие ключи

| Ключ | Сервис | Что задаёт | Дефолт |
|---|---|---|---|
| `config/application/app.security.jwk-set-uri` | station-controll (и любой resource-server) | URI JWK Keycloak для валидации JWT | Keycloak certs |
| `config/station-controll-service/station.offline-threshold-seconds` | station-controll | Окно тишины offline-свипа станций, сек | `21600` (6 ч) |
| `config/state-updater-service/app.services.station-controll.base-url` | state-updater | Куда ходить за станциями (`lb://` = по имени через discovery) | `lb://station-controll-service` |
| `config/state-updater-service/app.services.station-controll.connect-timeout-ms` | state-updater | Таймаут соединения клиента, мс | `5000` |
| `config/state-updater-service/app.services.station-controll.read-timeout-ms` | state-updater | Таймаут ответа клиента, мс | `30000` |

`config/application/*` читают все сервисы, `config/<app-name>/*` — только конкретный сервис
(имя = `spring.application.name`). Значение в KV **имеет приоритет** над yaml/ENV.

## Как это заведено в коде

- **JWK (hot-reload):** `JwtDecoderConfig` — `@RefreshScope ReactiveJwtDecoder`, читает
  `app.security.jwk-set-uri` (фолбэк — стандартное свойство/ENV). На refresh пересоздаётся с новым URI.
- **Порог свипа (hot-reload):** `StationTimeoutProperties` (`@ConfigurationProperties`), читается
  в `StationConnectivityService.sweepOffline()` в момент прогона → авто-rebind на refresh.
- **Клиент по имени + таймауты (hot-reload):** `stationControlWebClient` — `@RefreshScope` WebClient
  на `lb://station-controll-service`, таймауты из `StationClientProperties`.

## Сид значений по умолчанию

Автоматически — контейнером `consul-kv-init` в `docker-compose.local.yaml` / `docker-compose.prod.yaml`.
Вручную:

```bash
CONSUL_HTTP_ADDR=http://localhost:8500 ./consul/kv-seed.sh
```

## Изменить значение вживую (демо hot-reload)

```bash
# уменьшить порог offline-свипа до 5 минут — применится через ~1 c, без рестарта
curl -X PUT http://localhost:8500/v1/kv/config/station-controll-service/station.offline-threshold-seconds --data-binary '300'

# сменить Keycloak (JWK) на лету
curl -X PUT http://localhost:8500/v1/kv/config/application/app.security.jwk-set-uri \
  --data-binary 'http://keycloak:8080/realms/charging-stations/protocol/openid-connect/certs'

# принудительный рефреш конкретного сервиса (если не ждать watch):
curl -X POST http://localhost:8002/actuator/refresh   # state-updater
curl -X POST http://localhost:8001/actuator/refresh   # station-controll
```

Посмотреть текущее значение: `curl http://localhost:8500/v1/kv/<key>?raw` или Consul UI (`:8500`).

## Прод

В `docker-compose.prod.yaml` у station-controll выставлено `SPRING_CLOUD_CONSUL_CONFIG_ENABLED: "true"`,
у state-updater KV включён в yaml. Чтобы вернуть сервис на «только yaml/ENV», поставьте
`enabled=false` — код безопасно откатится на фолбэк-значения (пустой KV ничего не ломает).
