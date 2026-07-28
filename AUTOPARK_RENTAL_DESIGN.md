# Автопарк-аренда: дизайн бэкенда (встраивание в chargingStations)

> Черновик архитектуры для платформы-посредника «автопарки ↔ клиенты-арендаторы»,
> встраиваемой в существующий микросервисный монорепозиторий chargingStations.
> Составлено на основе изучения существующего кода (user-service, payment-service,
> booking-service, websocket-service, api-gateway) и документации StarLine API.

> ### Зафиксированные решения (2026-07-23)
> 1. **StarLine-аккаунт: personal на каждый автопарк** → adaptive polling обязателен, жёсткий
>    бюджет 1000 req/сутки/автопарк (см. §6.4 — важное следствие для «плавности» трекинга).
> 2. **KYC/реестр — гибрид:** роли в user-service+Keycloak; KYC/маппинг сотрудник↔автопарк — в autopark-service.
> 3. **Залог — только запись суммы** (без wallet-hold в payment на v1).
> 4. **Декомпозиция — единый autopark-service** (каталог + аренда + списание в одном сервисе).

---

## 1. Что переиспользуем как есть

| Компонент | Роль в новом проекте | Изменения |
|---|---|---|
| **keycloak** (realm `charging-stations`) | Единая identity | +3 роли (см. §7) |
| **user-service** (:8005) | Регистрация/логин/JWT, профиль | +роли, опц. привязка к автопарку |
| **payment-service** (:8007) | Кошелёк, авто-списание, залог | +consumer аренды, идемпотентность, hold залога |
| **notification-service** (:8006) | Email/уведомления | +новые типы событий |
| **api-gateway-service** (:8010) | Маршрутизация + swagger | +3 маршрута |
| **kafka / redis / consul** | Транспорт, кэш, discovery | +топики, +Redis GEO DB, +KV ключи |
| **websocket-service** (:8003) | НЕ трогаем (для зарядок) | Отдельный fleet-ws |

## 2. Новые сервисы (все WebFlux + R2DBC, паттерны из существующих)

| Сервис | Порт | Аналог в репо | Ответственность |
|---|---|---|---|
| **autopark-service** | 8011 | station-controll + booking | Каталог (автопарки, ТС, марки, кузова, фото, цены), бронь ТС, жизненный цикл аренды, планировщик посуточного списания, реестр клиентов + отзывы |
| **starline-integration-service** | 8012 | station-integration | ЕДИНСТВЕННЫЙ, кто ходит в StarLine API: авторизация/cookie, polling координат, команды блокировки двигателя. Мост Kafka ↔ StarLine REST |
| **fleet-websocket-service** | 8013 | websocket-service | Real-time push позиций ТС и событий аренды сотрудникам/владельцам/клиенту. Читает Redis Streams |

> **Рекомендация по декомпозиции:** каталог + аренда держим в одном `autopark-service`
> (аренда сильно связана с ТС/автопарком — вынос в отдельный сервис породил бы сагу там,
> где хватает локальной транзакции). А вот `starline-integration` и `fleet-websocket`
> — это чёткие bounded contexts (внешний адаптер + real-time push), их отделяем.

---

## 3. Ключевые потоки (упор на WebFlux + Kafka)

### 3.1 Просмотр каталога (синхронный REST)
```
Клиент → GW → autopark-service (R2DBC)
  GET /autopark/api/autoparks            (список + гео офисов)
  GET /autopark/api/autoparks/{id}/vehicles
  GET /autopark/api/vehicles/{id}        (деталка + фото + тарифная сетка)
```
Автопарков немного (офисы) — гео офисов держим в самой БД (lat/lng) или PostGIS.
Гео-поиск ближайших опционален.

### 3.2 Бронь ТС (до договора, бесплатно)
```
Клиент → POST /autopark/api/vehicles/{id}/book
  autopark-service: vehicle.is_booking=true, booking_client_id, TTL-хук
  → Kafka autopark.booking.events (VEHICLE_BOOKED)
       → notification-service (письмо/пуш сотруднику автопарка)
       → fleet-websocket-service (сотрудник видит новую бронь live)
  Планировщик авто-снятия брони если не сконвертирована в аренду за N часов
  (паттерн BookingStateScheduler).
```

### 3.3 Старт активной аренды (в офисе, роль AUTOPARK_SPECIALIST)
```
Сотрудник → POST /autopark/api/rentals
  body: vehicleId, clientId | (ФИО, ИНН, возраст → создать client_profile),
        deposit, rent_payment_time (время суток списания)
  autopark-service:
    - создать rent (is_active=true, current_rent_cycle=0, deposit, rent_payment_time)
    - vehicle.is_rent=true, rent_id, is_booking=false
    - upsert client_profile (реестр)
    - (опц.) hold залога в payment-service
  → Kafka autopark.rental.events (RENTAL_STARTED)
       → notification, fleet-websocket
       → starline.commands (UNBLOCK/ensure-active для устройства ТС)
```

### 3.4 ⭐ Посуточное авто-списание (требование №2) — сага по паттерну booking
```
autopark-service RentalBillingScheduler (@Scheduled ~каждую минуту):
  выбрать активные rent, у кого rent_payment_time == сейчас И день ещё не списан
  для каждого (идемпотентный ключ = rentId + cycleDay):
     resolve цену дня по тарифной сетке (current_rent_cycle → тариф)
     → Kafka rental.payment.requests {requestId, clientUuid, amount, rentId, cycleDay}

payment-service RentalPaymentRequestConsumer (аналог ChargingPaymentRequestConsumer):
     атомарно дебетует кошелёк (идемпотентно по requestId/cycleDay)
     → rental.payment.responses {requestId, success | INSUFFICIENT_FUNDS}

autopark-service RentalPaymentResponseConsumer:
  success:
     rent.current_rent_cycle++, total_profit += amount
     → autopark.rental.events (DAILY_CHARGE_OK) → ws + notification(чек)
     если было заблокировано → starline.commands (UNBLOCK_ENGINE)
  INSUFFICIENT_FUNDS:
     rent → состояние GRACE/SUSPENDED, зафиксировать «просрочку»
     (опц. grace-период перед блокировкой)
     → starline.commands (BLOCK_ENGINE, deviceId)
     → notification + ws + FCM «пополните баланс, двигатель будет/заблокирован»

Пополнение баланса (уже есть топик payment.events, см. память top-up):
  autopark-service слушает → повторяет списание за долг → при успехе UNBLOCK_ENGINE
```

### 3.5 ⭐ Live-трекинг ТС (требование №1)
```
starline-integration-service (единственный поллер):
  цикл по устройствам (adaptive: аренда — часто, простой — редко)
  GET /json/v3/device/{deviceId}/data (cookie slnet)
  → Kafka vehicle.telemetry {deviceId, vehicleId, lat, lng, ign, speed, ts}

Consumer телеметрии (в fleet-websocket или autopark-service):
  → Redis GEO (DB 2, per-autopark) + Redis Stream (per-autopark/vehicle)
  (паттерн state-updater-service: GEOADD + stream)

fleet-websocket-service:
  push позиций сотрудникам/владельцу (scope = auto_park_id)
  и клиенту, который сейчас арендует ТС (scope = clientId)
  (скелет StationWebSocketHandler: JWT ?token=, per-user сессии, PING/PONG)
```

### 3.6 Завершение аренды (клиент вернулся в офис)
```
Сотрудник → POST /autopark/api/rentals/{id}/finish
  rent.is_active=false, endedAt; vehicle.is_rent=false
  вернуть/закрыть залог (release hold или запись возврата)
  финальный расчёт неполного дня
  → RENTAL_COMPLETED → notification, ws, starline.commands(UNBLOCK/disarm)
```

### 3.7 Реестр клиентов + отзывы (требование №3)
```
Глобальный реестр всех, кто когда-либо арендовал (кросс-автопарк репутация):
  GET  /autopark/api/clients/{clientId}   → профиль + история аренд + просрочки + отзывы всех автопарков
  POST /autopark/api/clients/{clientId}/reviews  (роль OWNER/SPECIALIST)
  GET  /autopark/api/clients?search=ИНН|ФИО
Данные: client_profile + review в autoparkdb. История = rent-строки. Просрочки = события неуплаты.
Авторизация: читать реестр и писать отзывы может ЛЮБОЙ автопарк (в этом весь смысл), но только OWNER/SPECIALIST.
```

---

## 4. Уточнения к схеме БД (autoparkdb, PostgreSQL 16, R2DBC + Liquibase)

Правки к вашему черновику:

1. **Тарифы — нормализовать.** Вместо `rent_period.period varchar "1-3"`:
   ```
   pricing_plan(id, auto_park_id, name)
   pricing_tier(id, plan_id, min_days int, max_days int NULL, price numeric, currency)
       -- max_days NULL = «и далее» (ваше "8-")
   vehicle.pricing_plan_id → один план на много ТС (ровно ваша идея «назначить на все»)
   ```
2. **StarLine устройство — cookie НЕ на устройстве.** `slnet` живёт на уровне аккаунта (24ч) и протухает. Правильно:
   ```
   autopark_starline_account(auto_park_id, login, secret_ref, user_token_enc)  -- креды/долгоживущий токен
   starline_device(id, auto_park_id, device_id, vehicle_id,
                   model, can_track, can_block, block_channel)                 -- маппинг + CAPABILITY
   ```
   Управление `slnet` — централизованно в starline-integration-service (кэш + refresh).
   `can_block` зависит от МОНТАЖА (доп. канал заведён на блокирующее реле), а не от модели —
   см. §6.6. Логика неоплаты обязана деградировать: нет `can_block` → только уведомления + ручное действие.
3. **KYC клиента (ИНН/ФИО/отчество/возраст) — в autopark-service, не в user-service.**
   user-service = общая identity для ОБОИХ проектов (зарядки + аренда); не засоряем его
   доменным KYC. Роли — да, в user-service+keycloak; KYC/реестр — в autoparkdb (`client_profile`).
4. **Привязка сотрудник↔автопарк** — таблица `autopark_staff(user_uuid, auto_park_id, role)`
   в autoparkdb (или атрибут Keycloak). JWT несёт только роль; сервис резолвит автопарк.
5. **Идемпотентность списаний:** `rent_charge_ledger(id, rent_id, cycle_day, amount, status, request_id UNIQUE)`
   — защита от двойного списания при ретраях/редеплое.
6. **Единый ключ пользователя = Keycloak UUID** (`sub`). payment-service уже ключует кошелёк по UUID.
   В rent/client_profile храним `client_uuid uuid` (не Long id), чтобы стыковаться с payment без маппинга.
7. `vehicle_images` — переиспользовать паттерн загрузки файлов из station-controll (volume `uploads`).

---

## 5. Новые Kafka-топики (добавить в kafka-init)

| Топик | Producer → Consumer | Назначение |
|---|---|---|
| `autopark.booking.events` | autopark → notification, fleet-ws | бронь ТС |
| `autopark.rental.events` | autopark → notification, fleet-ws, payment | жизненный цикл аренды |
| `rental.payment.requests` | autopark → payment | request-reply посуточного списания |
| `rental.payment.responses` | payment → autopark | результат списания |
| `vehicle.telemetry` | starline-integration → fleet-ws/autopark | координаты/зажигание |
| `starline.commands` | autopark → starline-integration | BLOCK/UNBLOCK двигателя |
| `starline.command.responses` | starline-integration → autopark | подтверждение команды |
| `autopark.review.events` | autopark → (опц. audit) | отзывы о клиентах |
| (reuse) `payment.events` | payment → autopark | пополнение → ретрай списания/разблокировка |
| (reuse) `notification.events` | * → notification | письма |

---

## 6. StarLine — интеграция детально (критично)

### 6.1 Цепочка авторизации (централизована в starline-integration-service, кэшируется)
```
1. GET  https://id.starline.ru/apiV3/application/getCode
        ?appId=..&secret=MD5(appSecret)                     → code (живёт 1ч)
2. GET  https://id.starline.ru/apiV3/application/getToken
        ?appId=..&secret=MD5(appSecret+code)                → appToken (4ч, ОБЩИЙ на все автопарки)
3. POST https://id.starline.ru/apiV3/user/login
        header: token=appToken; body: login=<акк автопарка>, pass=SHA1(pass)
                                                            → user_token (slid) [+ возможна 2FA smsCode]
4. POST https://developer.starline.ru/json/v2/auth.slid
        body: {slid_token: user_token}                      → slnet (cookie, 24ч, per-автопарк)
Далее любые вызовы: header Cookie: slnet=<...>
```

### 6.2 Две операции, которые нам нужны
**A. Опрос позиции/состояния**
```
GET https://developer.starline.ru/json/v3/device/{deviceId}/data   (Cookie slnet)
   → lat/lng, ign (0/1), и пр. → publish vehicle.telemetry
```
**B. Блокировка/разблокировка двигателя (async, чтобы не держать reactive-поток)**
```
POST https://developer.starline.ru/json/v2/device/{deviceId}/async  {type, value}  → cmd_id
GET  https://developer.starline.ru/json/v2/device/{deviceId}/async/{cmd_id}         → статус (поллим до подтверждения)
```
> ⚠️ **Проверить точную команду под ваше железо.** В спеке `ign`/`ign_start`/`ign_stop` — это
> *дистанционный запуск/останов*. «Заблокировать так, чтобы клиент не смог завести» — часто это
> ДРУГОЙ контрол (реле/выход `out`, `arm`, штатный иммобилайзер). Список команд конкретной
> модели: `GET /json/device/{deviceId}/ctrls_library`. Нужно свериться с реальным трекером.

### 6.3 Управление жизненным циклом токенов
- Кэш `appToken` (4ч) — один на платформу; `slnet` (24ч) — per-автопарк.
- Проактивный refresh до истечения + on-401 повтор всей цепочки с джиттером/бэкоффом.
- `appId`/`appSecret` → Consul KV/secrets (существующий паттерн). Логин/пароль автопарка —
  шифровать в БД, а лучше хранить долгоживущий `user_token`, а не пароль.

### 6.4 Модель доставки и лимиты — ГЛАВНЫЙ РИСК (решение: personal-аккаунт на автопарк)
- **Вебхуков нет → только polling.** starline-integration — единственный поллер, буферизует
  в Kafka, изолирует остальную систему от квирков/лимитов StarLine (бэкпрешер).
- **Бюджет-математика (важно для продукта):** personal = **1000 запросов/сутки/автопарк** ≈
  **1 запрос каждые 86 секунд на ВЕСЬ автопарк**. Отсюда прямые следствия:
  - Опрос ОДНОЙ машины раз в 60с = 1440 запросов/сутки — **уже больше суточного лимита автопарка целиком**.
  - Реальный бюджет ~950 `/data`-поллов/сутки после накладных → делится на все видимые ТС.
  - Пример: автопарк из 20 машин, всех видно на карте → ~47 поллов/сутки/машину ≈ **позиция раз в ~30 мин**.
    Хотите чаще — придётся сокращать число одновременно опрашиваемых ТС.
  - ⚠️ Это **худший случай — поллинг каждой машины по отдельности**. См. batch-эндпоинт ниже.
- **⭐ Batch-эндпоинт `user_info` — главный рычаг (ПОДТВЕРДИТЬ у StarLine).** В классическом
  StarLine v2 есть запрос, отдающий ВСЕ устройства аккаунта за один вызов (позиции, скорость,
  состояние) — обычно `POST /json/v2/user/{user_id}/user_info`, `devices[]` с `position{x,y,s,ts}`,
  `car_state{ign,run}`, `alarm_state`. Если доступен:
  - 1 батч = позиции всех N машин → бюджет 1000/сутки → **все машины раз в ~90с на одном аккаунте**.
    Это переворачивает вывод выше: near-real-time для всего флота реально даже на personal.
  - ⚠️ В присланном OpenAPI (developer.starline.ru/spec) этот эндпоинт НЕ документирован —
    там только per-device `/data`. Уточнить доступность у `server@starline.ru`. Наивысший приоритет.
- **Adaptive polling (обязателен, если батча нет):** приоритезируем бюджет —
  - активная аренда: чаще (напр. каждые 3–5 мин),
  - забронировано/на выдаче: средне,
  - простой на стоянке: редко / по требованию (кнопка «обновить»),
  - гейтинг по движению: стоит → 1/час, `ign=1` → поднять частоту.
  Токен-refresh (slnet ~1/сутки) бюджет почти не ест.
- **Escape hatches, если лимита не хватает:** (а) несколько StarLine-аккаунтов на автопарк —
  шардинг устройств, N×1000/сутки (модель `autopark_starline_account` уже 1:N); (б) enterprise/бизнес-тариф
  (договорные лимиты + возможно server-side push-события движение/геозоны → поллинг «едет/стоит» не нужен).
  Адаптер абстрагируем так, чтобы personal→enterprise был сменой конфига.

### 6.5 ⚠️ Безопасность и юридический риск блокировки

**Конечный автомат аренды при неоплате (notify-before-block + block-only-when-stopped):**
```
ACTIVE
 └ списание не прошло → PAYMENT_DUE (старт grace-периода)
      уведомление «пополните до HH:MM, иначе блок при остановке»; пополнил/ретрай ок → ACTIVE
 └ grace истёк и не оплачено → PENDING_BLOCK
      ign==0 && speed==0 → СВЕЖИЙ контрольный поллинг → подтвердилось → BLOCK → BLOCKED
      едет → НЕ блокируем, ждём первой стоянки
 └ BLOCKED → пополнил → списываем долг → UNBLOCK → ACTIVE
```
**grace-период** = льготная отсрочка между «не смогли списать» и «блокируем» (конфиг в Consul KV,
напр. 6ч / до конца дня); в это время шлём напоминания. Клиента не блокируем в ту же секунду.

**Три слоя защиты (defense in depth):**
1. Софт (наш): гейтинг команды по `ign`/скорости из телеметрии + **свежий поллинг прямо перед блокировкой**
   (при polling'е последнее состояние может устареть на минуты). Если скорость недоступна в API — гейтим
   хотя бы по `ign==0`.
2. Хардвар (StarLine): использовать **speed-gated / «блок следующего старта»** контрол (свериться
   с `ctrls_library` и установщиком), НЕ жёсткое реле топлива на ходу.
3. Юр.: договор аренды явно разрешает дистанционную блокировку. Ручной override + алерт, если
   команда «блокировать» отправлена, но StarLine её не подтвердил.
- ⚠️ Гейтинг по скорости зависит от наличия поля скорости/`moving` в ответе StarLine — подтвердить.

### 6.6 Железо: StarLine Маяк M17 (одно из устройств заказчика)
- **Класс:** GPS+ГЛОНАСС маяк-трекер (скрытный, противоугонный). Питание: борт 12/24В + 2×CR123A (автономно).
- **Встроенный датчик движения (акселерометр)** — ловит старт движения → отлично для adaptive polling
  (поднимать частоту при движении). **Встроенный микрофон** (прослушка).
- **Блокировка двигателя — через УНИВЕРСАЛЬНЫЙ ДОП. КАНАЛ**, а не штатным иммобилайзером:
  - Возможна, **только если доп. канал при установке заведён на блокирующее реле/модуль**.
    → `can_block` — свойство КОНКРЕТНОЙ УСТАНОВКИ, не гарантия для всех ТС.
  - Команда в API = управление доп. выходным каналом (`out`/доп.канал через `set_param`/`async`),
    **НЕ `ign`**. Точное имя контрола — из `ctrls_library` конкретного M17.
  - Канал — «тупое» реле, не знает про скорость → **speed-gating целиком на нашем софте**
    (§6.5: блок только при `ign=0`/стоянке + свежий контрольный поллинг). Уточнить у установщика,
    что реле типа «запрет запуска», а не жёсткий обрыв на ходу.
- **Трекинг-нюанс:** маяк энергоэффективен → в автономном режиме координаты отдаёт РЕДКО (экономия батареи).
  Для аренды M17 должен быть на **бортовом питании** и настроен на частую отдачу, иначе live-трекинг
  будет разрежённым независимо от лимитов API.
- **Вывод:** флот скорее всего смешанный по возможностям → модель `can_track`/`can_block` per-device (§4.2)
  обязательна; UI сотрудника должен показывать, у каких ТС авто-блокировка доступна.

Источники: store.starline.ru/catalog/mayaki/starline_m17/, ugona.net/archive/tracker/starline/m17-1472.html

---

## 7. Изменения в инфраструктуре (чек-лист)

- [ ] **БД:** новый `autoparkdb` (postgres:16, +PostGIS если нужен гео-поиск офисов) + volume в docker-compose.prod.
- [ ] **Сервисы:** 3 новых контейнера (8011/8012/8013), сборка по паттерну `Dockerfile-services`.
- [ ] **Kafka topics:** добавить ~8 топиков в `kafka-init` (§5).
- [ ] **Keycloak realm:** роли `AUTOPARK_CLIENT`, `AUTOPARK_OWNER`, `AUTOPARK_SPECIALIST`.
      ⚠️ **Гоча (из памяти):** realm импортируется только на чистом старте — на живом Keycloak
      правка JSON не подхватится; добавлять роли через admin API/консоль + скрипт миграции.
- [ ] **user-service:** расширить `UserRole` enum (+`fromString` дефолт), эндпоинты смены роли,
      регистрация с автопарковыми ролями (keycloak-admin-client уже используется).
- [ ] **payment-service:** `RentalPaymentRequestConsumer`, идемпотентный посуточный дебет,
      механика залога (hold/capture/release — сейчас нет), Liquibase-миграции.
- [ ] **api-gateway:** маршруты `/autopark/**`, `/fleet-ws/**` (+ swagger-агрегация);
      `/starline/**` — внутренний, наружу не отдаём.
- [ ] **Redis:** DB 2 под гео ТС + отдельные stream-ключи; настроить `MAXLEN` (тримминг).
- [ ] **Consul:** регистрация 3 сервисов; KV: StarLine appId/secret, интервалы поллинга,
      время/grace посуточного списания.
- [ ] **nginx:** проброс `/autopark`, `/fleet-ws` (websocket upgrade-блок как для `/websocket`).
- [ ] **Наблюдаемость:** новые сервисы в otel-agent + дашборды/алерты.
- [ ] **Клиенты:** мобильный клиент (карта/каталог/бронь/аренда/кошелёк — многое переиспользуется);
      приложение/веб для сотрудников и владельцев (можно модулем в `chargingStationsAdmin`).

---

## 8. Оценка объёма и сроков (только бэкенд, инфра переиспользуется)

Оценка в **человеко-днях (dev-days)** при парном темпе со мной (буллит-пойнты бойлерплейта — быстро;
необратимый риск — StarLine и корректность денег).

| Фаза | Содержание | dev-days |
|---|---|---|
| 0 | Фундамент: роли (keycloak+user), autoparkdb, compose/gateway/consul, топики, скелеты 3 сервисов | 4–6 |
| 1 | Каталог: автопарки/ТС/марки/кузова/фото/тарифы, список/карта/деталка, сиды | 6–9 |
| 2 | Бронь ТС + уведомления сотруднику + авто-снятие + ws | 4–6 |
| 3 | Жизненный цикл аренды: старт/финиш, KYC/реестр, залог, стык с payment (request-reply) | 6–9 |
| 4 | ⭐ Посуточное авто-списание + блокировка/разблокировка: планировщик, идемпотентность, grace | 8–12 |
| 5 | ⭐ starline-integration: авторизация/cookie, поллинг, async-команды+статус, лимиты/резилентность | 8–12 |
| 6 | Live-трекинг: telemetry → Redis GEO/Streams → fleet-websocket (scope автопарк/клиент) | 6–9 |
| 7 | Реестр клиентов + отзывы: кросс-автопарк, история, просрочки, поиск | 4–6 |
| 8 | Хардненинг: authz-скоупинг, секреты, ретраи, реконсиляция, дашборды, e2e | 5–8 |
| | **Итого** | **≈ 51–77 dev-days** |

**Календарь:** ~10–15 недель для одного backend-разработчика; при активном парном режиме —
короче на бойлерплейте, но фазы **4 и 5 — несжимаемый риск** (внешний API + деньги).
Фронт (мобильный + сотрудники/владельцы) — отдельная оценка.

---

## 9. Подводные камни (ранжировано)

1. **StarLine без вебхуков + лимит 1000/сутки/аккаунт** — упор в лимит при трекинге флота.
   Нужны adaptive polling и/или enterprise-аккаунт. Самый большой архитектурный риск.
2. **Безопасность/ответственность блокировки двигателя** — нельзя блокировать в движении;
   grace, уведомления, ручной override, пункт в договоре, юр-ревью.
3. **Корректность денег / идемпотентность** — ровно одно списание за (rent, день); ретраи,
   редеплой, сдвиг часов → двойное/пропущенное. Нужны idempotency-key + ledger + реконсиляция
   (паттерн уже есть: `TopUpReconciliationScheduler`).
4. **Жизненный цикл токенов StarLine** (code 1ч / appToken 4ч / slnet 24ч) — централизованный
   refresh с бэкоффом; протухание в середине операции → повтор авторизации.
5. **Реактив vs блокирующий JPA** — autopark-service делаем на R2DBC (не повторяем исключение
   station-controll с блокирующим Hibernate). Но PostGIS в R2DBC — вручную; гео ТС уводим в Redis GEO.
6. **Keycloak realm import-only** — новые роли не появятся из правки JSON на живом Keycloak (гоча из памяти).
7. **Единый ключ пользователя** — payment ключует по Keycloak UUID (`sub`), user-service по Long id.
   В аренде везде используем UUID, иначе рассинхрон client_id/кошелька.
8. **Семантика залога** — «наличкой и записать» vs wallet-hold. Hold/capture/release в payment
   сейчас НЕТ — если нужен hold, это доп. работа.
9. **Часовые пояса времени списания** — `rent_payment_time` это wall-clock; TZ автопарка vs сервера,
   DST, пропуски при даунтайме → catch-up.
10. **Объём телеметрии в Redis** — тримминг стримов; scope per-автопарк/клиент (приватность —
    нельзя светить чужой флот).
11. **Authz-скоупинг** — сотрудник видит только свой автопарк; реестр/отзывы — кросс-автопарк by design.
    PII клиента (ИНН/ФИО) — требования к защите ПДн.
12. **Онбординг StarLine-аккаунта автопарка** — маппинг device_id↔ТС, получение списка устройств
    (bulk-эндпоинта в спеке нет), первичная настройка.
13. **Два websocket-сервиса** — разделение портов/маршрутов/nginx upgrade; переиспользовать JWT-валидатор.
14. **Сбой команды StarLine** — async-команда может не примениться; нужен цикл подтверждения и алерт,
    когда блокировка запрошена, но не подтверждена.

---

## 10. Решения и оставшиеся вопросы

**Зафиксировано (2026-07-23):**
1. ✅ StarLine — **personal-аккаунт на каждый автопарк** (бюджет 1000/сутки/автопарк → adaptive polling, §6.4).
2. ✅ KYC/реестр — **гибрид**: роли в user-service+Keycloak; KYC + маппинг сотрудник↔автопарк — в autopark-service.
3. ✅ Залог — **только запись суммы** (без wallet-hold на v1; hold/capture — если понадобится позже).
4. ✅ Декомпозиция — **единый autopark-service** (каталог + аренда + списание).

**Осталось уточнить (не блокирует старт Фазы 0–1):**
5. Точная модель StarLine-железа и команда «заблокировать двигатель» — свериться с `ctrls_library`
   конкретного трекера (`ign` — это дистанционный запуск, иммобилайзер может быть иным контролом).
6. Grace-период при недостатке средств: блокировать сразу в момент неоплаты или дать N часов/попыток?
   (рекомендация: конфигурируемый grace + уведомление до блокировки, блокировать только при `ign=0`).
