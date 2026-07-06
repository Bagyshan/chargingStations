# Mobile ↔ Backend API — документация интеграции

Документ для команды мобильного приложения. Здесь описываются HTTP-эндпоинты бэкенда,
которые использует мобильный клиент. Файл наполняется по мере связывания экранов с API.

---

## 0. Общее

### Базовый адрес

Все запросы идут через **единую точку входа** — nginx (`:80`), который проксирует на
`api-gateway-service`, а тот — на нужный микросервис.

```
http(s)://<host>/<service-prefix>/<путь внутри сервиса>
```

- `<host>` — домен/IP сервера (в проде задаётся при деплое).
- `<service-prefix>` — префикс сервиса на api-gateway. Gateway срезает префикс (`StripPrefix=1`)
  и передаёт остаток сервису.

Префиксы сервисов, которые нужны мобилке:

| Сервис | Префикс на gateway | Назначение |
|---|---|---|
| `state-updater-service` | `/state-updater` | Кэш станций (Redis): список, поиск, фильтры, гео |
| `station-controll-service` | `/station-controll` | Справочники (типы коннекторов и т.п.) |
| `user-service` | `/user` | Авторизация, профиль, избранное |
| `payment-service` | `/payment` | Кошелёк, пополнение |
| `booking-service` | `/booking` | Бронирование |
| `websocket-service` | `/websocket` | Realtime-обновления (WebSocket) |

> ⚠️ Важно: **не** обращайтесь к сервисам напрямую по их внутренним портам
> (8002, 8001 и т.д.) — они доступны только внутри docker-сети. Всегда используйте
> `http://<host>/<префикс>/...` через nginx.

### Авторизация

Эндпоинты получения станций (`/state-updater/...`) и список типов коннекторов
(`/station-controll/api/connector-types`) — **публичные**, JWT не требуется.
Авторизация (Bearer-токен) нужна для пользовательских действий: избранное, бронирование,
кошелёк (см. соответствующие разделы по мере добавления). Регистрация, вход и работа с
токенами описаны в **разделе 4**.

---

## 1. Станции для карты и списка

Источник данных — `state-updater-service`. Он держит актуальный кэш всех станций в Redis
(состояние коннекторов, тарифы, гео), поэтому именно отсюда мобилка получает станции —
это быстро и не нагружает основную БД.

### 1.1. Получить список станций (с фильтрами и гео)

```
GET /state-updater/api/cached-stations
```

Один эндпоинт обслуживает и экран карты, и список. Поддерживает:
- поиск по точке + расстояние до каждой станции + сортировку по близости;
- фильтр по радиусу;
- фильтры по доступности, свободным коннекторам, типам коннекторов, мощности и тарифам.

Все параметры **опциональны**. Без параметров вернётся весь список станций без расстояния.

#### Query-параметры

| Параметр | Тип | Описание |
|---|---|---|
| `longitude` | double | Долгота точки пользователя. **Передавать вместе с `latitude`.** Если переданы оба — в ответе появится `distanceTo` (км) и сортировка по возрастанию расстояния. |
| `latitude` | double | Широта точки пользователя. |
| `radiusKm` | double | Радиус поиска в километрах от точки. Работает **только** вместе с `longitude`+`latitude`. Станции дальше радиуса не возвращаются. Без координат игнорируется. |
| `availableOnly` | boolean (def. `false`) | Только «рабочие» станции: на связи (`online`) **и** не выведенные оператором из эксплуатации (`serviceStatus = IN_SERVICE`). |
| `freeConnectorsOnly` | boolean (def. `false`) | Только станции, у которых есть **хотя бы один свободный** коннектор (статус коннектора `Available`). |
| `connectorTypeIds` | список int | Фильтр по типам коннекторов. Станция проходит, если у неё есть коннектор одного из указанных типов. Формат: `connectorTypeIds=1,2` или `connectorTypeIds=1&connectorTypeIds=2`. ID берутся из эндпоинта 2.1. |
| `minPower` / `maxPower` | double | Диапазон мощности станции в кВт (ползунок «от/до»). Можно передать только одну границу. |
| `minKwCost` / `maxKwCost` | number | Диапазон стоимости за кВт·ч (ползунок). Можно передать только одну границу. |
| `minBookingMinuteCost` / `maxBookingMinuteCost` | number | Диапазон стоимости брони за минуту (ползунок). Можно передать только одну границу. |

**Семантика комбинации `freeConnectorsOnly` + `connectorTypeIds`:**
если заданы оба, станция проходит только когда у неё есть коннектор, который **одновременно**
свободен (`Available`) **и** относится к одному из выбранных типов. Это то, что нужно для
сценария «покажи станции, где прямо сейчас свободен коннектор моего типа». Если задан только
один из них — он применяется самостоятельно.

> Замечание про мощность: поле `power` хранится строкой (например `"60"` или `"60 kW"`).
> Бэкенд извлекает из неё число для сравнения. Если в значении нет числа, станция не пройдёт
> фильтр по `minPower`/`maxPower`.

> Все диапазонные/коннекторные фильтры объединяются по **И** (AND): станция должна
> удовлетворять всем заданным условиям одновременно.

#### Коды ответов

| Код | Когда |
|---|---|
| `200 OK` | Успех. Тело — объект `AllStationsResponse` (см. ниже). |
| `400 Bad Request` | Передан только один из `longitude`/`latitude`. В теле `error` с пояснением. |

#### Формат ответа

```json
{
  "stations": [
    {
      "stationId": "CB-1",
      "id": 1,
      "version": 12,
      "source": "station-control-service",
      "lastUpdated": "2026-06-28T10:15:30Z",
      "meterType": "kWh",
      "power": "60",
      "kwCost": 12.50,
      "bookingMinuteCost": 2.00,
      "serviceStatus": "IN_SERVICE",
      "online": true,
      "address": { "id": 5, "addressName": "пр. Чуй, 100" },
      "geolocation": { "lat": 42.8746, "lng": 74.5698 },
      "connectors": [
        {
          "connectorId": 1,
          "version": 3,
          "status": "Available",
          "connectorType": {
            "id": 2,
            "connectorTypeName": "CCS2",
            "connectorTypeIcon": "/files/connector-types/ccs2.png"
          }
        }
      ],
      "distanceTo": 1.42
    }
  ]
}
```

Примечания к полям:
- `stations` — массив станций. При ошибке присутствует поле `error` (строка), `stations` пустой.
- `distanceTo` — расстояние от переданной точки в **километрах**. Присутствует только если
  переданы `longitude`+`latitude`; иначе поля нет (`null`).
- `online` — станция на связи с центральной системой.
- `serviceStatus` — операторский статус (`IN_SERVICE` = в эксплуатации; иное = выведена).
- `connectors[].status` — OCPP-статус коннектора. Возможные значения: `Available` (свободен),
  `Preparing`, `Charging`, `SuspendedEV`, `SuspendedEVSE`, `Finishing`, `Reserved`,
  `Unavailable`, `Faulted`. Для UI «свободен» — это `Available`.
- `kwCost`, `bookingMinuteCost` — числа (могут быть `null`, если тариф не задан).

#### Примеры

Карта вокруг пользователя, в радиусе 5 км, только рабочие станции со свободным коннектором:
```
GET /state-updater/api/cached-stations?latitude=42.8746&longitude=74.5698&radiusKm=5&availableOnly=true&freeConnectorsOnly=true
```

Фильтр по типам коннекторов (CCS2=2, Type2=1) и мощности от 50 кВт:
```
GET /state-updater/api/cached-stations?connectorTypeIds=1,2&minPower=50
```

Список с диапазоном цены за кВт·ч и сортировкой по близости:
```
GET /state-updater/api/cached-stations?latitude=42.87&longitude=74.59&minKwCost=5&maxKwCost=15
```

---

### 1.2. Обогатить станции по списку ID (экран «Избранное»)

```
POST /state-updater/api/cached-stations/by-ids
```

Возвращает те же объекты, что и общий список, но только для переданных ID. Удобно для экрана
«Избранное»: мобилка хранит ID избранных станций, а актуальные данные (статусы, тариф,
расстояние) берёт здесь.

- **Тело запроса:** JSON-массив строк (stationId):
  ```json
  ["CB-1", "CB-2", "CB-7"]
  ```
- **Query (опц.):** `longitude`, `latitude` — как в 1.1; при наличии добавляют `distanceTo`
  и сортируют по близости. Без координат — порядок как в переданном массиве.
- Несуществующие/отсутствующие в кэше ID молча пропускаются.
- **Ответ:** `200 OK`, тело — `AllStationsResponse` (см. 1.1). Пустой/отсутствующий массив → `stations: []`.
- `400`, если передан только один из `longitude`/`latitude`.

---

### 1.3. Получить одну станцию по ID

```
GET /state-updater/api/cached-stations/{stationId}
```

- **Ответ:** `200 OK` — объект станции `StationStateDTO` (та же структура, что элемент `stations[]`
  в 1.1, но без `distanceTo`).
- `404 Not Found`, если станции нет в кэше.

Пример: `GET /state-updater/api/cached-stations/CB-1`

---

### 1.4. Количество станций (служебное)

```
GET /state-updater/api/cached-stations/count
```
Ответ: `{ "count": 42, "hashKey": "...", "timestamp": "..." }`. Для отладки/счётчиков.

---

## 2. Справочники

### 2.1. Список типов коннекторов (для фильтра)

Используется, чтобы наполнить фильтр «Тип коннектора» на экране карты/списка. Полученные `id`
передавайте в `connectorTypeIds` эндпоинта 1.1.

```
GET /station-controll/api/connector-types
```

- **Авторизация:** не требуется.
- **Ответ:** `200 OK`, массив:
  ```json
  [
    {
      "id": 1,
      "connectorTypeName": "Type 2",
      "connectorTypeIcon": "http://<host>/files/connector-types/type2.png",
      "connectorsCount": 24
    },
    {
      "id": 2,
      "connectorTypeName": "CCS2",
      "connectorTypeIcon": "http://<host>/files/connector-types/ccs2.png",
      "connectorsCount": 11
    }
  ]
  ```
- `connectorTypeIcon` — абсолютный URL иконки (бэкенд сам подставляет хост).
- `connectorsCount` — сколько физических коннекторов этого типа заведено в системе (для UI
  можно игнорировать).

---

## 3. Зарядка (старт / стоп транзакции)

Старт и остановка зарядки идут через `station-controll-service` (префикс `/station-controll`).
Оба эндпоинта **требуют авторизацию** — Bearer-токен пользователя. Пользователь (`userId`)
определяется бэкендом из токена, **в теле его передавать не нужно**.

> ℹ️ **Важно про `ocppTag`.** Раньше клиент передавал `ocppTag` в теле — **больше не нужно**.
> Тег станции хранится на бэкенде и подставляется автоматически. Мобилка отправляет только
> `chargeBoxId` и `connectorId`. Если старый клиент всё же пришлёт `ocppTag` — поле будет
> проигнорировано, ошибки не будет.

> ⚠️ **Про путь.** Как и для остальных эндпоинтов, обращайтесь через nginx + префикс gateway:
> `http://<host>/station-controll/api/stations/...`. Префикс (`/station-controll`) задаётся на
> api-gateway и теоретически может измениться при изменении конфигурации — путь *внутри* сервиса
> (`/api/stations/start-transaction`) стабилен. Не обращайтесь к сервису по внутреннему порту (8001).

### 3.1. Запустить зарядку

```
POST /station-controll/api/stations/start-transaction
Authorization: Bearer <JWT>
Content-Type: application/json
```

**Тело запроса:**
```json
{
  "chargeBoxId": "CB-1",
  "connectorId": 1
}
```

| Поле | Тип | Обяз. | Описание |
|---|---|---|---|
| `chargeBoxId` | string | да | ID станции (тот же `stationId`, что в списке станций, разд. 1). |
| `connectorId` | int | да | Номер коннектора на станции (`connectors[].connectorId` из разд. 1). |

Перед стартом бэкенд проверяет: станция найдена и доступна (на связи, в эксплуатации,
коннектор не занят/не неисправен/не забронирован другим) **и** что на кошельке пользователя
хватает баланса хотя бы на 1 кВт·ч.

#### Коды ответов

| Код | Когда | Что показать пользователю |
|---|---|---|
| `200 OK` | Зарядка запущена. Тело — `TransactionResponseDTO` (ниже). | Экран «идёт зарядка». |
| `402 Payment Required` | Недостаточно средств на кошельке. | Предложить пополнить баланс. |
| `403 Forbidden` | Коннектор забронирован другим пользователем. | «Станция занята/забронирована». |
| `404 Not Found` | Станция или коннектор не найдены. | «Станция недоступна». |
| `409 Conflict` | Коннектор не в рабочем состоянии (не операционный). | «Коннектор недоступен». |
| `503 Service Unavailable` | Станция выведена из эксплуатации или offline. | «Станция временно недоступна». |
| `401 Unauthorized` | Нет/просрочен токен. | Повторная авторизация. |

> При кодах `403/404/409/503` в ответе есть заголовок **`X-Unavailable-Reason`** с машинной
> причиной: `STATION_NOT_FOUND`, `CONNECTOR_NOT_FOUND`, `OUT_OF_SERVICE`, `OFFLINE`,
> `RESERVED_BY_OTHER`, `NOT_OPERATIONAL`. Можно использовать для точного текста ошибки.

### 3.2. Остановить зарядку

```
POST /station-controll/api/stations/stop-transaction
Authorization: Bearer <JWT>
Content-Type: application/json
```

**Тело запроса** — то же, что у старта:
```json
{
  "chargeBoxId": "CB-1",
  "connectorId": 1
}
```

Останавливается активная транзакция на указанном коннекторе. `transactionId` передавать не нужно.

#### Коды ответов

| Код | Когда |
|---|---|
| `200 OK` | Запрос на остановку отправлен. Тело — `TransactionResponseDTO`. |
| `401 Unauthorized` | Нет/просрочен токен. |

### 3.3. Формат ответа `TransactionResponseDTO`

Одинаков для старта и стопа:

```json
{
  "transactionId": 12345,
  "chargeBoxId": "CB-1",
  "connectorId": 1,
  "startValue": "0",
  "stopValue": null,
  "actionType": "START_TRANSACTION",
  "status": "ACTIVE",
  "userId": "a1b2c3d4-...",
  "startTimestamp": "2026-06-29T10:15:30Z",
  "stopTimestamp": null,
  "pricePerKwh": 12.50,
  "maxKwQuantity": 8.0
}
```

| Поле | Описание |
|---|---|
| `transactionId` | ID транзакции зарядки. |
| `chargeBoxId`, `connectorId` | Станция и коннектор. |
| `actionType` | `START_TRANSACTION` или `STOP_TRANSACTION`. |
| `status` | `ACTIVE` (идёт), `COMPLETED` (завершена), `CANCELLED`, `REJECTED`. |
| `startValue` / `stopValue` | Показания счётчика (Вт·ч) на старте/стопе. `stopValue` = `null` пока зарядка идёт. |
| `startTimestamp` / `stopTimestamp` | Время начала/окончания (ISO-8601, UTC). |
| `pricePerKwh` | Цена за кВт·ч, зафиксированная на момент старта. |
| `maxKwQuantity` | Максимум кВт·ч, который покрывает баланс пользователя (лимит зарядки). |
| `ocppTag` | Технический тег станции (для мобилки не нужен). |

> 💡 Прогресс зарядки в реальном времени (текущие кВт·ч, стоимость, SoC, статус) приходит не из
> ответа этих эндпоинтов, а по **WebSocket** — `chargingEvent`, см. раздел **6. Реалтайм**.
> Старт/стоп — это команды, а не поток состояния.

---

## 4. Авторизация: регистрация и вход

Авторизация обслуживается `user-service` (префикс `/user`). Базовый путь внутри сервиса —
`/api/v1/auth`. Итоговый URL: `http://<host>/user/api/v1/auth/<...>`.

Identity-провайдер — **Keycloak**; `user-service` выдаёт стандартные JWT (access + refresh).
Access-токен кладётся в заголовок `Authorization: Bearer <accessToken>` для всех защищённых
запросов (зарядка, избранное, кошелёк, бронирование, профиль).

### 4.0. Общий формат ответа (`ApiResponse`)

Все эндпоинты `user-service` возвращают единую обёртку:

```json
{
  "success": true,
  "message": "Login successful",
  "data": { ... },          // полезная нагрузка (или null)
  "timestamp": "2026-06-29T15:51:07.39"
}
```

- `success` — `true`/`false`. При ошибке `success=false`, в `data` иногда лежат детали.
- Полезные данные авторизации (`AuthResponse`) — **внутри `data`**, а не в корне.
- При ошибках валидации (`400`) в `data` — карта `поле → сообщение`:
  ```json
  {
    "success": false,
    "message": "Request validation failed",
    "data": { "email": "Invalid email format", "password": "Password must be at least 8 characters" }
  }
  ```

### 4.1. Регистрация

```
POST /user/api/v1/auth/register
Content-Type: application/json
```

**Тело запроса:**
```json
{
  "email": "user@mail.kg",
  "password": "Passw0rd123",
  "firstName": "Азамат",
  "lastName": "Иванов",
  "phone": "+996700123456"
}
```

| Поле | Тип | Обяз. | Ограничения |
|---|---|---|---|
| `email` | string | да | валидный email |
| `password` | string | да | см. **политику паролей** ниже |
| `firstName` | string | нет | ≤ 100 символов |
| `lastName` | string | нет | ≤ 100 символов |
| `phone` | string | нет | формат E.164: `+996...` (без пробелов/дефисов) |

> `role` бэкенд игнорирует для публичной регистрации — всегда `USER`.

> 🔐 **Политика паролей (Keycloak).** Пароль должен удовлетворять всем условиям:
> длина ≥ 8, минимум 1 цифра, 1 строчная буква, 1 заглавная буква, 1 спецсимвол,
> и пароль не должен совпадать с email/username. Пример валидного: `120176_Saikal`.
> ⚠️ При нарушении политики бэкенд сейчас отвечает **`500`** с обобщённым сообщением
> (настоящая причина «password policy not met» теряется в общем обработчике). Поэтому
> **проверяйте пароль на клиенте до отправки** — в приложении это делает
> `core/utils/password_policy.dart`.

**Успех — `201 Created`.** В `data` приходит **только пользователь, без токенов** —
аккаунт ещё не активен, нужно подтвердить email:
```json
{
  "success": true,
  "message": "User registered successfully",
  "data": {
    "user": {
      "id": 42,
      "email": "user@mail.kg",
      "phone": "+996700123456",
      "firstName": "Азамат",
      "lastName": "Иванов",
      "role": "USER",
      "emailVerified": false,
      "phoneVerified": false
    }
  }
}
```

После регистрации на email уходит письмо со ссылкой подтверждения. **Войти до подтверждения
нельзя** (см. 4.2). UI: после `201` показываем экран «Проверьте почту».

#### Коды ответов

| Код | Когда | Что показать |
|---|---|---|
| `201 Created` | Пользователь создан, письмо отправлено. | Экран «Проверьте почту». |
| `400 Bad Request` | Ошибка валидации (email/пароль). `data` — карта полей. | Подсветить поля. |
| `409 Conflict` | Email или телефон уже заняты. | «Пользователь уже существует». |

### 4.2. Вход

```
POST /user/api/v1/auth/login
Content-Type: application/json
```

**Тело запроса:**
```json
{ "email": "user@mail.kg", "password": "Passw0rd123" }
```

**Успех — `200 OK`.** В `data` — `AuthResponse` с токенами и пользователем:
```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "accessToken": "eyJhbGciOi...",
    "refreshToken": "eyJhbGciOi...",
    "tokenType": "Bearer",
    "expiresIn": 300,
    "scope": "profile email",
    "user": {
      "id": 42,
      "email": "user@mail.kg",
      "phone": "+996700123456",
      "firstName": "Азамат",
      "lastName": "Иванов",
      "role": "USER",
      "emailVerified": true,
      "phoneVerified": false
    }
  }
}
```

Мобилка сохраняет `accessToken` и `refreshToken`, во все защищённые запросы добавляет
`Authorization: Bearer <accessToken>`. `expiresIn` — время жизни access-токена в **секундах**
(обычно короткое, ~5 мин) — по истечении обновляйте через `refresh` (4.3).

#### Коды ответов

| Код | Когда | Что показать |
|---|---|---|
| `200 OK` | Вход выполнен, токены в `data`. | Перейти на главный экран. |
| `400 Bad Request` | Ошибка валидации тела. | Подсветить поля. |
| `404 Not Found` | Пользователь не найден / неверная пара email-пароль (бэкенд отвечает `"Invalid credentials"`). | «Неверный email или пароль». |
| `500` | **Особый случай** — см. ⚠️ ниже (в т.ч. «email не подтверждён»). | «Email не подтверждён либо сервис недоступен». |

> ⚠️ **Известная особенность бэкенда (на момент написания).**
> Вход **с неподтверждённым email** и вход с **неверным паролем** существующего
> пользователя бэкенд отдаёт как `500` с обобщённым сообщением
> `"Internal server error..."` (а не как `401/403`). Отличить «email не подтверждён» от
> реального сбоя по ответу нельзя — показывайте пользователю общий текст
> «Email не подтверждён или сервис временно недоступен». То же касается регистрации с
> паролем, не прошедшим политику (см. 4.1) — поэтому пароль валидируется на клиенте.

### 4.3. Обновление токена

```
POST /user/api/v1/auth/refresh
Content-Type: application/json
```

**Тело:** `{ "refreshToken": "<refreshToken>" }`

**Успех — `200 OK`**, в `data` — новый `AuthResponse` (новые `accessToken`/`refreshToken`,
без `user`). Вызывайте, когда access-токен истёк (или превентивно при `401` на защищённом
запросе), затем повторяйте исходный запрос с новым токеном. Если refresh тоже невалиден —
разлогинить пользователя и отправить на экран входа.

### 4.4. Сброс пароля

```
POST /user/api/v1/auth/password/reset-request
Content-Type: application/json
```

**Тело:** `{ "email": "user@mail.kg" }`

**Успех — `200 OK`** — на email отправлена ссылка для сброса. `404`, если пользователь с
таким email не найден. Сам сброс пароля (`POST /password/reset` с токеном из письма)
выполняется по ссылке из письма — мобилке отдельный экран не нужен.

### 4.5. Подтверждение email

Подтверждение происходит **по ссылке из письма** (`GET /user/api/v1/auth/verify-email?token=...`),
которую пользователь открывает в браузере. Мобилке вызывать этот эндпоинт не нужно — после
подтверждения пользователь возвращается в приложение и логинится.

> ℹ️ Эндпоинты `check-email` и `verify-email/request` (повторная отправка письма) **требуют
> авторизацию** и из неавторизованного флоу мобилки недоступны — не используйте их на экранах
> регистрации/«Проверьте почту».

### 4.6. Выход

```
POST /user/api/v1/auth/logout
Authorization: Bearer <accessToken>
```

Серверной инвалидации токена сейчас нет — практически выход = просто удалить сохранённые
токены на клиенте. Вызов эндпоинта опционален.

---

## 5. Бронирование коннектора

Бронирование обслуживается `booking-service` (префикс `/booking`). Базовый путь внутри сервиса —
`/api/bookings`. Итоговый URL: `http://<host>/booking/api/bookings/...`. Все эндпоинты
**требуют Bearer-токен**; `userId` бэкенд берёт из токена — в теле передавать не нужно.

> ⚠️ **Бронь ≠ зарядка.** Это два независимых процесса с разной тарификацией:
> - **Бронь** — платное удержание коннектора, тарифицируется **поминутно** (`bookingMinuteCost`
>   станции, сом/мин). Не требует и не запускает зарядку.
> - **Зарядка** (раздел 3) — энергетическая транзакция, тарифицируется **за кВт·ч** (`kwCost`).
>
> Можно бронировать, не заряжая; можно заряжать без брони. На экране коннектора это две
> отдельные кнопки: **«Забронировать»** (`#FFA20D`) и **«Зарядить»** (`#5A2E5C`).

> ℹ️ Ответы `booking-service` — «сырой» JSON (как у раздела 3), **без** обёртки `ApiResponse`
> (та есть только у `user-service`, разд. 4).

### 5.1. Создать бронь

```
POST /booking/api/bookings
Authorization: Bearer <JWT>
Content-Type: application/json
```

**Тело запроса:**
```json
{
  "stationId": "CB-1",
  "connectorId": 1
}
```

| Поле | Тип | Обяз. | Описание |
|---|---|---|---|
| `stationId` | string | да | ID станции (тот же, что `stationId`/`chargeBoxId` в разд. 1 и 3). |
| `connectorId` | int | да | Номер коннектора (`connectors[].connectorId`). |

Бэкенд запускает **сагу** (Kafka request-reply, таймаут 7 с): параллельно спрашивает
`payment-service` (баланс) и `station-controll-service` (станция доступна?). Если всё ок —
считает `maxBookingMinutes = баланс / ценаЗаМинуту` и создаёт бронь со статусом `ACTIVE`.

#### Ответ `BookingResponse` (HTTP 200)

```json
{
  "bookingId": "1ef3e5b5-065e-4e0d-826b-ca698713c952",
  "status": "ACTIVE",
  "maxBookingMinutes": 28,
  "startedAt": "2026-06-30T13:55:40.153340606Z",
  "remainingBookingEndTime": "2026-06-30T14:23:40.153340606Z",
  "endedAt": null,
  "errorMessage": null
}
```

| Поле | Описание |
|---|---|
| `bookingId` | UUID брони — нужен для завершения (5.2) и сопоставления WebSocket-событий. |
| `status` | `ACTIVE` — бронь создана; `FAILED` — сага отклонила (см. `errorMessage`). |
| `maxBookingMinutes` | На сколько минут хватает баланса по тарифу брони. |
| `startedAt` | Время начала (ISO-8601, UTC). |
| `remainingBookingEndTime` | Когда бронь истечёт, если её не завершить (= `startedAt` + maxMinutes). |
| `endedAt` | `null`, пока бронь активна. |
| `errorMessage` | Текст причины при `status = FAILED` (иначе `null`). |

> ⚠️ **`HTTP 200` ещё не значит «успех».** При нехватке баланса/недоступности станции бэкенд
> отвечает **200** с телом `status: "FAILED"` и заполненным `errorMessage`. Клиент обязан
> проверять `status`, а не только HTTP-код. (Сетевые/прочие ошибки — обычными кодами `4xx/5xx`.)

> ℹ️ `pricePerMinute` в ответе **не возвращается** — для оценки и отображения стоимости берите
> `bookingMinuteCost` станции из раздела 1.

### 5.2. Завершить бронь

```
POST /booking/api/bookings/{bookingId}/complete
Authorization: Bearer <JWT>
```

Освобождает коннектор и фиксирует итоговую сумму **за фактически прошедшее время**.

#### Ответ `BookingCompleteResponse` (HTTP 200)

```json
{
  "bookingId": "1ef3e5b5-065e-4e0d-826b-ca698713c952",
  "status": "COMPLETED",
  "totalSum": 0.00,
  "totalMinutes": 0,
  "startedAt": "2026-06-30T13:55:40.153341Z",
  "endedAt": "2026-06-30T13:55:43.229656468Z",
  "errorMessage": null
}
```

| Поле | Описание |
|---|---|
| `status` | `COMPLETED`. |
| `totalSum` | Списанная сумма = `ценаЗаМинуту × totalMinutes`. |
| `totalMinutes` | Прошедшие **полные** минуты (секунды округляются вниз). |
| `startedAt` / `endedAt` | Период брони. |

> 💡 Время округляется вниз до минут: бронь длиной < 1 мин даёт `totalMinutes: 0`,
> `totalSum: 0.00`. (На этом же механизме в будущем планируется бесплатное окно — первые
> N минут бесплатно, настраиваемые через админку; пока бесплатна только неполная минута.)

> ℹ️ Отдельного «бесплатного отменить» эндпоинта сейчас нет — отмена брони = `complete`
> (за прошедшее время спишется оплата, обычно 0, если завершить сразу).

### 5.3. Жизненный цикл и завершение по времени

- Пока бронь `ACTIVE`, `booking-service` каждые ~10 с шлёт прогресс (для WebSocket, см. 5.4).
- Если время вышло (`remainingBookingEndTime`), бэкенд-планировщик **сам** переведёт бронь в
  `COMPLETED` — клиенту дополнительно вызывать `/complete` не нужно.
- Клиенту достаточно хранить `bookingId` и показывать обратный отсчёт до `remainingBookingEndTime`.

### 5.4. Реалтайм брони по WebSocket

Живые показания брони приходят через общий WebSocket-канал — см. раздел **6. Реалтайм
(WebSocket)**. Для брони важен `bookingEvent`:

- `RESERVATION_PROGRESS` (каждые ~10 с) — обновляет `minutesElapsed`, `currentCost`,
  `remainingBookingMinutes`, `estimatedEndTime`, `maxBookingMinutes` (последнее растёт
  после пополнения баланса во время брони — `RESERVATION_BALANCE_UPDATED`).
- `RESERVATION_COMPLETED` / `RESERVATION_CANCELLED` / `RESERVATION_PAYMENT_FAILED` —
  бронь закрыта (в т.ч. автозавершение планировщиком по времени). Клиент показывает итог
  **без** вызова `/complete`.

---

## 6. Реалтайм (WebSocket)

Единый канал на всё приложение (один на залогиненного пользователя):

```
ws://<host>/websocket/ws/station-events?token=<JWT>
```

nginx (`:80`) проксирует Upgrade → api-gateway (`Path=/websocket/**`, `StripPrefix=1`) →
`websocket-service` (`/ws/station-events`). **Токен передаётся только в query-параметре**
`?token=` (не в заголовке) — сервер валидирует его как JWT и маршрутизирует личные события
по `sub`. Для `https` хоста используйте `wss://`.

### 6.1. Формат сообщений (`WebSocketMessageDTO`)

```json
{
  "type": "EVENT",
  "bookingEvent":  { … },   // личное событие брони (или null)
  "chargingEvent": { … },   // личное событие зарядки (или null)
  "event":         { … },   // широковещательное событие станции (или null)
  "stationId": "CB-1",
  "message": null,
  "timestamp": 1782817466371,
  "serverTime": 1782817466371
}
```

`type`:
| Тип | Смысл | Действие клиента |
|---|---|---|
| `PING` | сервер шлёт каждые ~30 с | **обязательно** ответить `{"type":"PONG","timestamp":<ms>}` |
| `EVENT` | полезная нагрузка в `bookingEvent` / `chargingEvent` / `event` | разобрать и применить |
| `ERROR` | ошибка (например, неверный формат) | залогировать |
| `SUBSCRIPTION` / `UNSUBSCRIPTION` | подтверждение подписки на станцию | опционально |

> ⚠️ Если не отвечать на `PING`, сервер посчитает клиента неактивным. Дополнительно у
> сервера есть жёсткий таймаут соединения (~5 мин) — соединение периодически
> переподключается, это нормально (см. 6.4).

### 6.2. Личное событие брони — `bookingEvent`

```json
{
  "eventType": "RESERVATION_PROGRESS",
  "reservationId": "1ef3e5b5-…",
  "userId": "e6315d7d-…",
  "data": {
    "stationId": "CB-1",
    "connectorId": 1,
    "pricePerMinute": 15.0,
    "minutesElapsed": 3,
    "currentCost": 45.0,
    "maxBookingMinutes": 28,
    "remainingBookingMinutes": 25,
    "startedAt": "2026-06-30T13:55:40Z",
    "estimatedEndTime": "2026-06-30T14:23:40Z"
  }
}
```
`eventType`: `RESERVATION_CREATED`, `RESERVATION_STARTED`, `RESERVATION_PROGRESS`,
`RESERVATION_BALANCE_UPDATED`, `RESERVATION_COMPLETED`, `RESERVATION_CANCELLED`,
`RESERVATION_PAYMENT_FAILED`. `reservationId` = `bookingId` из 5.1.

### 6.3. Личное событие зарядки — `chargingEvent`

```json
{
  "userId": "e6315d7d-…",
  "chargeBoxId": "CP_TEST_2",
  "connectorId": 1,
  "transactionId": 6,
  "energyKwh": 0.45,
  "currentCost": 11.70,
  "kwCost": 26.0,
  "maxKwQuantity": 6.021,
  "startedAt": 1782854004.0,
  "soc": null,
  "status": "ACTIVE"
}
```
Приходит на каждое meter-value инициатора (на CP_TEST_2 — раз в ~15 с, энергия-регистр
растёт на ~0.15 кВт·ч). `status`: `ACTIVE` (идёт), либо терминальный
`COMPLETED` / `CANCELLED` / `REJECTED` / `Faulted` (зарядку нужно закрыть). `transactionId`
= из ответа `start-transaction` (3.1) — по нему сопоставляем сессию.

> ⚠️ **Что бэкенд НЕ присылает (важно для клиента):**
> - **`power`** (мгновенная мощность) — поля нет вовсе. Клиент вычисляет её сам из прироста
>   энергии между кадрами: `power_kW = Δ energyKwh / (Δ t / 3600)` (на CP_TEST_2 ≈ 36 кВт).
>   Раньше экран показывал статичные «20 кВт» — это была номинальная мощность коннектора.
> - **`soc`** — приходит **только** если станция шлёт measurand `SO_C`. CP_TEST_2 шлёт `null`,
>   поэтому процент заряда авто показать нельзя — кольцо/подпись переключаются на «% от лимита
>   сессии» (`energyKwh / maxKwQuantity`).
> - `startedAt` / `timestamp` сериализуются как **epoch-секунды с дробью** (`1782854004.0`),
>   а не ISO — парсер дат это учитывает. `currentCost` = `energyKwh × kwCost` (проверено).

Личные события (`bookingEvent`/`chargingEvent`) сервер шлёт **только инициировавшему
пользователю** (роутинг по `sub` JWT). Широковещательный `event` (изменения станций)
получают все — клиент по нему освежает список станций.

### 6.4. Требования к клиенту (реализовано в мобилке)

1. **Всегда подключён.** Одно соединение держится, пока пользователь залогинен.
   Переподключение при любом обрыве (плохая сеть, рестарт сервиса, серверный таймаут) —
   с нарастающей паузой `1→2→4→8→12→15 с`.
2. **PONG.** На каждый `PING` автоматически отправляется `PONG`.
3. **Свежий токен.** Перед каждым подключением берётся актуальный access-токен; при
   истечении — тихий refresh (`POST /user/api/v1/auth/refresh`).
4. **Возврат в приложение / сеть.** На `AppLifecycleState.resumed` соединение форсированно
   проверяется и при необходимости переподключается.
5. **«Сторож тишины».** Если от сервера >75 с нет ни одного кадра — соединение считается
   мёртвым и переустанавливается.
6. **Восстановление состояния.** Если приложение перезапустили во время активной
   брони/зарядки, первый же `RESERVATION_PROGRESS` / `chargingEvent(status=ACTIVE)`
   **восстанавливает** активную сессию из события (имя станции/тип коннектора — из кэша
   станций по `stationId`).

### 6.5. Карта реализации (Flutter)

| Файл | Роль |
|---|---|
| `data/api/ws_client.dart` | низкоуровневый самовосстанавливающийся клиент: connect/`ready`, PING→PONG, backoff-reconnect, idle-watchdog, refresh токена |
| `data/models/ws_message.dart` | парсинг `WebSocketMessageDTO` + `WsBookingEvent` / `WsChargingEvent` / `WsStationEvent` |
| `state/realtime_provider.dart` | владеет клиентом, раздаёт события в провайдеры брони/зарядки/станций, отдаёт статус канала |
| `state/booking_provider.dart` → `applyServerEvent` | живые значения брони + восстановление + фиксация итога |
| `state/charging_provider.dart` → `applyServerEvent` | реальные показания зарядки (заменяют симуляцию) + восстановление + завершение |
| `app.dart` → `_RealtimeHost` | привязка к авторизации и `AppLifecycleState` |
| `widgets/realtime_dot.dart` | индикатор «Онлайн / Подключение…» на живых экранах |

> 🔁 **Хэндофф симуляции.** До первого реального кадра показания плавно тикают локально
> (1 с) по формулам бэкенда. Как только приходит `bookingEvent`/`chargingEvent` —
> авторитетные значения с сервера перекрывают локальную оценку (для зарядки симуляция
> энергии при этом полностью отключается).

### 6.6. Производные показания зарядки (клиент)

Meter-value приходит раз в ~15 с, но пользователь должен видеть плавную картину **каждую
секунду**. Поэтому `ChargingProvider` держит секундный тикер и между серверными кадрами
интерполирует значения, а на каждый реальный кадр — «защёлкивает» их на авторитетные:

| Показатель | Источник / правило |
|---|---|
| **Мощность, кВт** | вычисляется из Δ энергии между кадрами; на 1-м кадре — средняя с момента старта; потолок 350 кВт (фильтр выбросов) |
| **Время** | `now − startedAt`, тикает ежесекундно (тикер шлёт `notifyListeners`) |
| **Энергия, кВт·ч** | серверный `energyKwh`; между кадрами растёт по вычисленной мощности (≤ `maxKwQuantity`) |
| **Стоимость** | серверный `currentCost`; между кадрами = `energyKwh × kwCost` |
| **Остаток баланса** | `баланс_на_старте − currentCost` (уменьшается в реальном времени); списание в кошелёк — разово по завершении |
| **Кольцо/прогресс** | `energyKwh / maxKwQuantity` (доля энергии от лимита сессии) |
| **Процент заряда** | реальный `soc`, если станция прислала; иначе — `% от лимита` |

Интерполяция останавливается, если кадров нет дольше 30 с (поток замер) — значения не «уплывают».

> 🛠️ **Возможное расширение бэкенда (на будущее).** Если станция начнёт слать measurand
> `POWER_ACTIVE_IMPORT` / `SO_C`, эти значения можно добавить в `ChargingStatusEvent`
> (station-controll) → `ChargingStatusDTO` (websocket) → `WsChargingEvent`, и клиент возьмёт
> их как авторитетные вместо вычисленных. Пока их нет — работает клиентский расчёт.

---

## 7. Кошелёк и оплата (payment-service, O!Dengi)

Сервис: `payment-service`, префикс шлюза **`/payment`** (StripPrefix=1 → пути сервиса
начинаются с `/api/v1/balance`). Все запросы требуют Bearer-токен.

**Модель — предоплаченный кошелёк.** Пользователь пополняет баланс через O!Dengi
(dengi.kg, QR-pay), а зарядка/бронь списывают с баланса. Отдельного «списка списаний»
на бэке пока нет — история зарядок ведётся в приложении локально и мержится с историей
пополнений с сервера.

### 7.1. Идентификатор пользователя (`userId`)

Payment-service ключует кошелёк по **keycloakId = `sub` из JWT** (UUID), а не по
числовому `id` из `user-service`. Мобилка достаёт `sub` из access-токена
(`TokenStore.userId`, декодер `core/utils/jwt.dart`) и подставляет в путь.

> ⚠️ Эндпоинты `/{userId}/...` не сверяют путь с `sub` токена (только `authenticated()`).
> Клиент всегда передаёт свой собственный `sub`. Возможное усиление на будущее —
> `/me`-эндпоинты, выводящие userId из JWT на бэке (как в booking/station-controll).

### 7.2. Баланс

`GET /payment/api/v1/balance/{userId}` → `BalanceDto`:

```json
{ "userId": "5b0e…-uuid", "balance": 84.00, "isBooking": false }
```

Строка кошелька автосоздаётся с нулём, если её ещё нет (404 не возвращается).

### 7.3. Создать счёт на пополнение (O!Dengi invoice)

`POST /payment/api/v1/balance/{userId}/top-up/initiate`

```json
{ "amount": 500.00, "description": "Wallet top-up" }   // amount в сомах, ≥ 1.00
```

Ответ `201` — `TopUpResponse`:

```json
{
  "id": "…", "userId": "…", "orderId": "b3f1…(hex)", "invoiceId": "991580730580",
  "amount": 500.00, "currency": "KGS", "status": "PENDING",
  "qrUrl": "https://test4-mwallet.dengi.kg/qr.php?...",   // картинка QR (Image.network)
  "linkApp": "https://o.kg/l/a?t=wl_unpbill&id=…",         // deeplink в приложение O!Dengi
  "paylinkUrl": "https://test-paylink.dengi.kg/#00…",      // веб-страница с банками
  "createdAt": "…", "paidAt": null
}
```

`status` ∈ `PENDING | APPROVED | CANCELED | FAILED`. Клиент показывает **QR-картинку**
(`qrUrl`) + кнопку «Открыть страницу оплаты» (`paylinkUrl`, `url_launcher` → внешний
браузер; на странице — O!Bank/MBANK/BakAi/Demir/Optima/RSK/KICB/MegaPay…).

### 7.4. Опрос статуса пополнения

`GET /payment/api/v1/balance/top-up/{orderId}/status` → `TopUpResponse`.

Бэкенд, если счёт ещё `PENDING`, **сам синхронно сверяется с O!Dengi** (`statusPayment`)
перед ответом. Клиент опрашивает раз в **3 с** (таймаут 5 мин), плюс кнопка «Я оплатил ·
проверить статус». На `APPROVED` — обновляет баланс (`refresh`) и показывает успех; на
`CANCELED/FAILED` — возвращает к вводу суммы с сообщением.

> Подтверждение оплаты на бэке дублируется: webhook `result_url`
> (`POST /payment/api/v1/payments/callback`, HMAC-MD5, без JWT) **и** планировщик-сверка
> каждые 30 с. Кошелёк кредитуется ровно один раз (идемпотентно по `order_id`).

### 7.5. История пополнений

`GET /payment/api/v1/balance/{userId}/top-up/history` → `[TopUpResponse]` (свежие сверху).
Клиент берёт только `APPROVED` и мержит с локальными записями зарядок.

### 7.6. Топ-ап во время активной сессии

Если баланс пополнен во время активной **брони** или **зарядки**, payment-service шлёт
`BalanceUpdatedEvent` в Kafka `payment.events`; booking-service/station-controll продлевают
сессию под новый баланс и эмитят `RESERVATION_BALANCE_UPDATED` — приложение уже ловит его
по WebSocket (см. §6). То есть пополнение «на лету» расширяет доступное окно без перезапуска.

### 7.7. Карта реализации (Flutter)

| Слой | Файл |
|---|---|
| Декодер JWT (`sub`) | `core/utils/jwt.dart`, `data/api/token_store.dart` (`userId`) |
| Модели | `data/models/top_up.dart` (`TopUp`, `TopUpStatus`, `BalanceInfo`) |
| API | `data/api/payment_api.dart` (`getBalance`, `initiateTopUp`, `topUpStatus`, `topUpHistory`) |
| Состояние | `state/wallet_provider.dart` (`refresh`/`refreshBalance`, баланс+история, `deduct`/`addChargeRecord`) |
| Экран пополнения | `features/wallet/topup_screen.dart` (сумма → QR/paylink + опрос → успех) |
| Экран кошелька | `features/wallet/wallet_screen.dart` (`refresh` при открытии + pull-to-refresh) |
| Баланс при входе | `app.dart` `_RealtimeHost` (`wallet.refresh()` после логина) |
| Реконсиляция | `charging_provider._finishLocally` (`refreshBalance` через 4 с после стопа) |
| Deps / манифест | `pubspec.yaml` (`url_launcher`), `android/app/src/main/AndroidManifest.xml` (`<queries>` http/https) |

**«Живой остаток» при зарядке:** во время сессии баланс уменьшается оптимистично локально
(`WalletProvider.deduct`, `liveBalance = startBalance − cost`), а сервер-истина
подтягивается `refreshBalance` после завершения (бэк списывает итог асинхронно через Kafka
settlement, поэтому реконсиляция с задержкой ~4 с).

---

## 8. Профиль пользователя (user-service)

Вкладка **«Профиль»** (нижняя навигация) полностью работает поверх `user-service`
(gateway-префикс `/user`, все ответы завёрнуты в `ApiResponse<T>` — см. §4.0).
Пользователь определяется бэкендом **по `email` из JWT**, поэтому id в путях не нужен.
Все запросы требуют `Authorization: Bearer <accessToken>`.

### 8.1. Получить профиль

```
GET /user/api/v1/users/profile
Authorization: Bearer <accessToken>
```

Ответ (`data`) — `UserProfileResponse`:

```json
{
  "id": 12,
  "email": "bagishan01@gmail.com",
  "phone": "+996709324447",
  "firstName": "Bagyshan",
  "lastName": "Kadyrkulov",
  "role": "USER",
  "emailVerified": true,
  "phoneVerified": false,
  "active": true,
  "createdAt": "2026-06-20T12:34:56",
  "lastLoginAt": "2026-07-02T08:47:01"
}
```

Экран показывает имя/роль, email и телефон с бейджами **подтв./не подтв.**
(`emailVerified`/`phoneVerified`), счётчик избранных станций.

### 8.2. Обновить профиль

```
PUT /user/api/v1/users/profile
Authorization: Bearer <accessToken>
Content-Type: application/json

{ "firstName": "Bagyshan", "lastName": "Kadyrkulov", "phone": "+996709324447" }
```

- Отправляем только заданные поля (`UpdateUserRequest`: `firstName`, `lastName`, `phone`).
- **Телефон валидируется бэкендом по `^\+?[1-9]\d{1,14}$`** — без пробелов/скобок/дефисов.
  Клиент нормализует номер перед отправкой (оставляет ведущий `+` и цифры).
  При нарушении формата бэк отдаёт `400` с текстом ошибки — показываем его пользователю.
- Пустой телефон в запрос не включаем (иначе `@Pattern` отклонит пустую строку).
- Ответ — обновлённый `UserProfileResponse` (тот же формат, что и §8.1).

### 8.3. Подтверждение email

Проверять статус можно полем `emailVerified` из профиля (или `GET /user/api/v1/verification/status`).
Если email не подтверждён — на экране показывается баннер с кнопкой «Отправить», которая
повторно шлёт письмо:

```
POST /user/api/v1/verification/email/resend
Authorization: Bearer <accessToken>
```

> **Телефон:** SMS-верификация на бэкенде **не реализована** (эндпоинты закомментированы,
> TODO). Поэтому в приложении фейковый SMS-флоу убран — телефон просто редактируемое поле,
> `phoneVerified` остаётся `false` до появления бэкенда.

### 8.4. Смена пароля (по email)

Отдельного «сменить пароль по старому+новому» на бэке нет — используем **сброс по email**
(тот же эндпоинт, что и «Забыли пароль» на логине, §4.4). Пункт «Сменить пароль» шлёт ссылку
на email пользователя:

```
POST /user/api/v1/auth/password/reset-request        (публичный, без Bearer)
{ "email": "<email пользователя>" }
```

### 8.5. Выход

```
POST /user/api/v1/auth/logout        (best-effort; бэк пока не ведёт blacklist)
Authorization: Bearer <accessToken>
```

Клиент вызывает logout (пока токен ещё есть), затем локально чистит `TokenStore`,
`ProfileProvider` и переводит на экран входа.

### 8.6. Карта реализации (Flutter)

| Слой | Файл |
|---|---|
| Модель | `data/models/user_profile.dart` (`UserProfile` + `fromJson`/`copyWith`/`roleLabel`) |
| API | `data/api/user_api.dart` (`getProfile`, `updateProfile`, `resendVerificationEmail`, `requestPasswordReset`, `logout`) |
| Состояние | `state/profile_provider.dart` (`load`/`updateProfile`/`resendVerificationEmail`/`requestPasswordReset`/`clear`) |
| Экран профиля | `features/profile/profile_screen.dart` (загрузка при открытии + pull-to-refresh, бейджи, баннер email, logout) |
| Редактирование | `features/profile/edit_profile_screen.dart` (async-сохранение, нормализация телефона) |
| Профиль при входе | `app.dart` `_RealtimeHost` (`profile.load(silent:true)` после логина, `clear()` при выходе) |

**Проверено вживую** (34.136.86.114, пользователь bagishan01@gmail.com): профиль загрузился
реальными данными (имя, роль USER, email `подтв.`, телефон `не подтв.`), `PUT` сохранил
профиль (снекбар «Профиль сохранён»).

---

## 9. Избранные станции (user-service)

Хранится только membership — список `chargeBoxId`. Полные данные станций приложение
берёт из своего кэша станций по этим id (второй запрос не нужен). Пользователь
определяется по `sub` из JWT. Все ответы — обёртка `ApiResponse{success,message,data}`.
Gateway-префикс `/user`.

### 9.1 Список избранного

```
GET /user/api/v1/favorites          (Bearer)
→ 200 ApiResponse<List<String>>     data: ["CB-1","CB-3", ...]
```

### 9.2 Добавить / удалить

```
POST   /user/api/v1/favorites/{chargeBoxId}   (Bearer) → 200 ApiResponse
DELETE /user/api/v1/favorites/{chargeBoxId}   (Bearer) → 200 ApiResponse
```

### 9.3 Реализация в приложении

| Слой | Файл / приём |
|---|---|
| API | `data/api/favorites_api.dart` (`list`, `add`, `remove`) |
| Состояние | `state/favorites_provider.dart` — набор id, **оптимистичный** `toggle` (мгновенно + серверная синхронизация с откатом при ошибке), `load`/`clear` |
| Загрузка при входе | `app.dart` `_RealtimeHost`: `favorites.load()` после логина, `clear()` при выходе |
| Сердечко | `widgets/favorite_button.dart` (без изменений — `toggle` по-прежнему возвращает `bool`) |
| Экран | `features/favorites/favorites_screen.dart` (фильтрует кэш станций по `isFavorite`) |

> Замена мока `MockData.favoriteIds()` на реальный бэкенд. Эндпоинты уже развёрнуты на
> проде — работает без передеплоя.

---

## 10. История: зарядки и брони

Две новые серверные ручки истории текущего пользователя (плюс уже реальные пополнения
кошелька из §7). Обе отдают **сырой JSON-массив**, новые записи — первыми.

### 10.1 История зарядок (station-controll)

```
GET /station-controll/api/transactions      (Bearer)
→ 200 [ TransactionHistoryDTO, ... ]
```

`TransactionHistoryDTO`: `transactionId, chargeBoxId, connectorId, status
(ACTIVE|COMPLETED|CANCELLED|REJECTED), reason, startTimestamp, stopTimestamp,
startValue, stopValue, transactionValue (Wh), totalSum (сом), pricePerKwh,
maxKwQuantity, createdAt`.

> Путь **`/api/transactions`** — аутентифицируемый, в отличие от публичного
> read-only `GET /api/stations/**`. Поэтому история именно здесь, а не под `/api/stations`.
> `userId` = `sub` из JWT. Блокирующий JPA вынесен на `boundedElastic`.

### 10.2 История броней (booking-service)

```
GET /booking/api/bookings                   (Bearer)
→ 200 [ BookingHistoryResponse, ... ]
```

`BookingHistoryResponse`: `bookingId, stationId, connectorId, status
(ACTIVE|COMPLETED|FAILED), pricePerMinute, maxBookingMinutes, totalMinutes,
totalSum, startedAt, endedAt, createdAt`.

### 10.3 Реализация в приложении

| Слой | Файл / приём |
|---|---|
| Модели | `data/models/history_item.dart` (`ChargeHistoryItem`, `BookingHistoryItem` + `fromJson`) |
| API | `data/api/transaction_api.dart` → `history()`; `data/api/booking_api.dart` → `history()` |
| Состояние | `state/history_provider.dart` (`load` — грузит вкладки **независимо** через `Future.wait`, per-tab ошибки `chargesError`/`bookingsError`, итоги кВт·ч/сумма, `clear`) |
| Экран | `features/history/history_screen.dart` — 3 вкладки: **Зарядки / Брони / Платежи** (первые две с бэкенда, платежи — из кошелька), pull-to-refresh, имена станций из кэша `StationsProvider.byId` |
| Вход в раздел | из кошелька (`wallet_screen`) и из меню «Профиль» → «История» |
| Загрузка/сброс | `app.dart` `_RealtimeHost`: `history.load(silent: true)` при входе, `history.clear()` при выходе |

> ⚠️ Эти два эндпоинта **новые** — до передеплоя `booking-service` и
> `station-controll-service` на проде вкладки «Зарядки»/«Брони» вернут ошибку/404.
> «Платежи» работают сразу (payment-service уже развёрнут).

> **Устойчивость (fix):** вкладки грузятся раздельно — падение одного эндпоинта
> (напр. `/api/bookings`) больше **не** гасит другую вкладку. Раньше `Future.wait`
> падал целиком: ошибка броней стирала и список зарядок («после перезахода всё
> исчезло»).

---

## 11. Завершение сессии сервером → принудительный экран итога

Когда сессию завершает **сервер** (исчерпан баланс/бюджет, авто-стоп, поломка
коннектора, истечение брони), приложение обязано закрыть экран активной
сессии и показать итог. Раньше это ломалось:

**Зарядка (station-controll).** Поток `station.meter.values` шлёт статус только
пока транзакция `ACTIVE`; после STOP значения больше не идут, а последний
отправленный статус был `ACTIVE`. `updateStopTransactionAndAck` теперь дополнительно
публикует **терминальный** `ChargingStatusEvent` (status = `COMPLETED`/…, с итоговыми
`energyKwh`/`currentCost`) в `charging.user.status` → websocket-service роутит его
инициатору → мобилка получает терминальный `chargingEvent` и закрывает экран.

**Бронь (booking-service).** `BookingStateScheduler` проверял `endedAt`, который у
активной брони **не заполняется** (заполняется `remainingBookingEndTime`), поэтому
бронь никогда не завершалась по истечении. Теперь проверка идёт по
`remainingBookingEndTime`; при завершении выставляются `totalSum`/`totalMinutes`,
шлётся `RESERVATION_COMPLETED` (в `booking.state` → мобилке) **и** `STOP_RESERVATION`
(в `booking.events` → payment-service списывает оплату).

**Мобилка — принудительный переход.** `state/session_nav.dart`: глобальный
`navigatorKey` + флаги `chargingScreenVisible`/`bookingScreenVisible` (ставятся в
`initState`/`dispose` активных экранов). Координатор в `app.dart` `_RealtimeHost`
слушает `ChargingProvider`/`BookingProvider`: при появлении `summary`, **если**
активный экран не открыт (пользователь ушёл на карту/в профиль), сам пушит
`SessionSummaryScreen`/`BookingSummaryScreen`. Если активный экран открыт — он
показывает итог сам (без дубля перехода).

> ⚠️ Требует передеплоя `station-controll-service` и `booking-service`.

---

## 12. Смена пароля в приложении (текущий + новый)

Раньше «Сменить пароль» слал письмо-сброс (`/auth/password/reset-request`) — надо
было покидать приложение. Теперь смена происходит **прямо в приложении**.

**Backend (user-service, НУЖЕН передеплой).** Новый защищённый эндпоинт:
`POST /user/api/v1/users/password`, тело `ChangePasswordRequest{currentPassword,
newPassword}` (newPassword ≥ 6). `UserService.changePassword` проверяет текущий
пароль, пробуя взять токен через `keycloakService.getUserKeycloakInstance(email,
current)`, и при успехе ставит новый через `keycloakService.resetPassword(...)`.
Неверный текущий пароль → **`InvalidPasswordException` → 400** (специально не 401,
иначе Dio-интерсептор запустил бы авто-refresh и мог бы разлогинить). Email берётся
из JWT-claim `email`.

**Mobile.** `UserApi.changePassword`, `ProfileProvider.changePassword`, новый экран
`features/profile/change_password_screen.dart` (текущий/новый/подтверждение, показ
пароля, валидация ≥6 и «не совпадает со старым»). Пункт «Сменить пароль» открывает
этот экран (диалог с письмом убран). Старый `requestPasswordReset` оставлен для
«забыли пароль» на экране входа.

## 13. Живое отслеживание верификации email

Эндпоинт `GET /user/api/v1/verification/status` → `{emailVerified,phoneVerified}`
(уже был на бэке — **без изменений**). Мобилка теперь им пользуется:
`UserApi.emailVerified` + `ProfileProvider.refreshVerification/startVerificationWatch/
stopVerificationWatch`. На экране профиля, если email не подтверждён, запускается
опрос раз в 5 с; как только подтверждён — таймер сам останавливается и бейдж
«не подтв.» → «подтв.» без перелогина. В баннере две кнопки: «Отправить письмо»
(resend, тоже стартует слежение) и «Я подтвердил» (разовая сверка). Таймер гасится
в `dispose` профиля и в `clear()` при логауте.

## 14. Радиус поиска: ползунок + окружность на карте

Фильтр по расстоянию от опорной точки (пока центр Бишкека — та же точка, от которой
считается `distanceKm` и стоит маркер «вы»).

- `StationFilters.radiusKm` (0 = выкл) в `_match`: станция за радиусом отсекается;
  `StationsProvider.setRadiusKm/radiusKm` меняет фильтр «вживую» (notify).
- Карта (`map_screen.dart`): FAB-`radar` справа, сразу над кнопкой «моя геолокация»,
  открывает нижнюю панель с `Slider` 1–30 км и живым счётчиком «N станц. в радиусе».
  Гео-окружность рисуется как `Fill`-полигон (72 точки, `_circlePolygon` по гаверсинусу)
  — реальный радиус в км, корректный на любом зуме. Станции вне радиуса **скрываются
  полностью** (пин удаляется с карты; превью выбранной станции закрывается, если она
  выпала из зоны). «Выключить» сбрасывает радиус в 0 и убирает окружность.
- **Тапы по пинам при активном радиусе:** у `maplibre_gl` по умолчанию `Fill` рисуется
  поверх кругов и входит в `annotationConsumeTapEvents` — полигон радиуса перехватывал
  нажатия по станциям внутри зоны. Исправлено на `MapLibreMap`: `annotationOrder`
  `[fill, line, circle, symbol]` (fill снизу) + `annotationConsumeTapEvents: [circle]`.
- Список станций: радиус доступен и в общей шторке фильтров (`filters_sheet.dart`,
  секция «Радиус поиска», `Slider` 0–30 км, 0 = Выкл) — шторка открывается и с карты
  (`tune`), и со страницы списка; счётчик «Показать N станций» учитывает радиус.
- **Синхронизация с отдельной кнопкой радиуса:** источник правды один —
  `StationFilters.radiusKm` в `StationsProvider`. Панель радиуса на карте показывает
  фактическое значение фильтра (0 = «Выкл», без фантомного «5 км»), её ползунок —
  0–30 км; «Сбросить» в шторке применяется **сразу** (не ждёт «Показать»), поэтому
  окружность/пины/кнопка `radar` обновляются мгновенно.
- **Локация на будущее:** добавлен `geolocator` + разрешения `ACCESS_FINE/COARSE_LOCATION`
  в манифест; `core/services/location_service.dart` (`ensurePermission` вызывается при
  открытии карты, `currentPosition` — задел). Центр радиуса пока фиксирован; переключение
  на реальный GPS — точечная замена `_center`/опорной точки без переделки UI.

## 15. Фильтры станций: range-ползунки (UX)

Шторка фильтров (`features/map/filters_sheet.dart`) переработана; бэкенд не участвует —
фильтрация клиентская по данным `cached-stations`.

- **Диапазонные фильтры** (двухточечный `RangeSlider`): «Мощность» (кВт, по
  `maxPowerKw` станции), «Тариф зарядки» (сом/кВт·ч, `tariffPerKwh`), «Тариф брони»
  (сом/мин, `bookingMinuteCost`). В `StationFilters` — пары nullable-границ
  (`powerMinKw/powerMaxKw`, `priceMin/priceMax`, `bookingMin/bookingMax`);
  `null` = граница не задана.
- **Границы ползунков**: всегда `0..max`, где max — разумный дефолт (мощность
  350 кВт, тариф зарядки 50 сом/кВт·ч, тариф брони 30 сом/мин), автоматически
  расширяющийся, если в данных станций есть значения выше
  (`StationsProvider.powerBounds / priceBounds / bookingBounds`). Ползунки видны
  всегда — даже когда станция одна и разброса в данных нет. Ползунок у края =
  граница снята; полный диапазон = фильтр неактивен, подпись «Любая/Любой».
- **Все изменения применяются вживую** (как ползунок радиуса на карте): каждый
  чип/ползунок/тумблер сразу вызывает `applyFilters` — карта (окружность, пины),
  список и индикаторы кнопок обновляются мгновенно, из какой бы страницы шторка
  ни была открыта (карта или список). Кнопка внизу — «Готово · N станций»
  (просто закрывает, счётчик живой). «Сбросить» тоже мгновенный.
- Кнопка `tune` на карте подсвечивается фиолетовым при любых активных фильтрах
  (как кнопка фильтра на странице списка).
- **Карта применяет все фильтры**: пины станций, не проходящих фильтр (радиус,
  мощность, тарифы, тип коннектора, свободные/доступные), убираются с карты
  (`StationsProvider.matchesFilters` в `_syncCircles`); превью выбранной станции
  закрывается, если она выпала из фильтра. Поисковый запрос на пины не влияет —
  строка поиска относится к списку.
- Шторка обёрнута в `SingleChildScrollView` — не переполняется на малых экранах.

## 16. Bat-маркеры станций на карте + кластеризация

Кастомные маркеры вместо кругов (`map_screen.dart`, чисто клиентское):

- **Форма** (по референсу `chargingStationsMobile/ChatGPT Image 3 июл. …png`):
  пин с двумя «рожками» сверху, вогнутой дугой между ними и острым кончиком
  снизу (`_pinShape`, безье-контур), белая обводка, **белый круг в центре** —
  внутри него тёмная молния (станция) или число (кластер). Кончик пина
  указывает точно на координату (`iconAnchor: 'bottom'`). Bitmap рисуется в
  рантайме (`_drawMarker`: Canvas → PNG → `controller.addImage`), кэш по ключу.
- **Окрас по коннекторам**: сегмент цвета на каждый коннектор станции
  (зелёный `free` / оранжевый `busy` / серый `unavailable`, статусы
  отсортированы), между сегментами узкий градиентный переход
  (`ui.Gradient.linear` со стопами, blend 7%). Ключ картинки — строка
  статусов (`im-st-fb` и т.п.), т.е. на каждую комбинацию рисуется один раз.
- **Кластеризация**: клиентская grid-кластеризация в мировых пикселях
  Web Mercator текущего зума (`_worldPx`, ячейка ~76px) — при отдалении
  близкие станции сливаются в больший пин с **фирменным градиентом**
  (оранжевый → маджента → фиолетовый, как на референсе) и **числом станций**
  в белом круге. Тап по кластеру приближает камеру (+1.8 зума),
  `onCameraIdle` пересобирает маркеры при смене зума ≥0.4.
- **Технически**: Symbol-аннотации maplibre_gl (`addSymbol` +
  `onSymbolTapped`), `annotationConsumeTapEvents: [symbol]`,
  `trackCameraPosition: true`. Картинки рендерятся в физических px:
  станция 96×116 при `iconSize 1.0` ≈ 44dp (подобрано по эмулятору).
  Тап по символу может продублироваться `onMapClick` — сброс выбора
  игнорируется 600мс после выбора маркера (иначе превью закрывалось сразу).
  Маркеры уважают все фильтры (§15) — не прошедшие фильтр станции
  не попадают и в кластеры.

---

## 17. Реальный GPS (позиция пользователя)

Чисто клиентское (`location_service.dart`, `stations_provider.dart`, `map_screen.dart`):

- **Источник позиции**: `geolocator` — `getLastKnownPosition()` + постоянный
  `getPositionStream(distanceFilter: 40м)`. Разово `getCurrentPosition` НЕ
  вызывается: его register/unregister GNSS-слушателей блокировал main thread
  binder-вызовом `unregisterGnssNmeaCallback` (ловили ANR на эмуляторе).
- **Провайдер**: `StationsProvider.ensureLocationTracking()` (идемпотентно,
  вызывается при открытии карты и по кнопке my_location). Позиция хранится в
  `userLat/userLng` (+ `hasGpsFix`); до первого fix — центр Бишкека (фолбэк,
  в т.ч. при отказе в разрешении).
- **Расстояния**: `Station.distanceKm` мутабельно — при каждом смещении
  пересчитывается локально хаверсином от реальной позиции (без REST); при
  первом fix дополнительно выполняется `refresh()` (серверная сортировка
  от настоящей точки).
- **Карта**: маркер «вы», центр окружности радиуса и кнопка центрирования
  привязаны к живой позиции; при первом fix камера мягко центрируется
  на пользователе (`newLatLngZoom(…, 13.5)`).
- Проверка на эмуляторе: `adb emu geo fix <lng> <lat>` — расстояние в списке
  меняется вживую (2.2 км → 414 м при перемещении точки).

---

## 18. WebSocket: полный контракт сообщений

Канал один: `ws://<host>/websocket/ws/station-events?token=<JWT>`. Сервер шлёт
`WebSocketMessageDTO{type, event|bookingEvent|chargingEvent, message, …}`.

**Типы сообщений и реакция клиента** (`ws_message.dart`, `realtime_provider.dart`):

| type | Что внутри | Реакция |
|---|---|---|
| `PING` | — | авто-`PONG` (в `WsClient`, наружу не отдаётся) |
| `EVENT` + `bookingEvent` | персональная бронь | `BookingProvider.applyServerEvent` |
| `EVENT` + `chargingEvent` | персональная зарядка | `ChargingProvider.applyServerEvent` |
| `EVENT` + `event` | широковещательное событие станции | `StationsProvider.applyStationEvent` (in-place) |
| `SUBSCRIPTION`/`UNSUBSCRIPTION` | ack подписки | no-op |
| `ERROR` | `message` | debug-лог |

**Станционные события** (`StationEventDTO.eventType`) применяются к каталогу
**без REST-перезапроса**:

- `STATION_CREATED/UPDATED/LOCATION_UPDATED/VERSION_INCREMENTED` — полное
  состояние `stationState{online, serviceStatus, connectors[]}` → статусы
  коннекторов патчатся in-place (та же логика, что при разборе REST:
  `!online || serviceStatus != IN_SERVICE` ⇒ все коннекторы недоступны);
- `CONNECTOR_STATUS_CHANGED` — `metadata{connectorId, newStatus}` → патч
  одного коннектора (`StationsApi.mapOcppStatus`);
- `STATION_DELETED` — станция убирается из каталога;
- `TARIFF_UPDATED` — `eventData[]{stationId, kwCost, bookingMinuteCost}` →
  живое обновление тарифов (поля `Station` мутабельны);
- `METER_VALUE` — игнор (личная зарядка идёт отдельным `chargingEvent`);
- неизвестная станция (`STATION_CREATED`) → троттлёный полный refresh (≥5 c).

**Опрос как страховка**: пока WS подключён — фоновый REST-опрос раз в 3 мин;
при обрыве — раз в 30 с; при реконнекте — catch-up `refresh()` (добор
пропущенного за время без канала).

**Фикс бэкенда** (`websocket-service`, НУЖЕН REDEPLOY): убран безусловный
обрыв соединения через 5 минут — теперь idle-таймаут (2 мин без PONG/сообщений,
проверка каждые 30 с). Живой клиент, отвечающий на PING, держит соединение
бесконечно; мобильный реконнект остаётся страховкой.

---

## 19. Push-уведомления (FCM)

Полная инструкция по включению: **`FIREBASE_SETUP.md`** (создать Firebase-проект,
заполнить `lib/core/config/firebase_config.dart`, положить service-account JSON
в `./secrets/`). Без ключей всё работает в режиме no-op.

- **Мобилка**: `firebase_core`/`firebase_messaging`, инициализация из Dart-значений
  (`FirebaseConfig`, без google-services.json и правок gradle). После логина —
  `PushService.registerAfterLogin()`: разрешение (Android 13+), FCM-токен →
  `POST /user/api/v1/users/device-tokens {token, platform}`; `onTokenRefresh` →
  повторная регистрация; при выходе — `DELETE …/device-tokens?token=` +
  `deleteToken()`. В форграунде пуши не показываются (события уже идут по WS).
- **user-service** (REDEPLOY): таблица `device_tokens` (upsert по токену —
  устройство может сменить владельца), пользовательские endpoint'ы под JWT и
  внутренние `GET/DELETE /api/v1/internal/device-tokens*` под общим секретом
  `X-Internal-Token` (env `INTERNAL_API_TOKEN`).
- **notification-service** (REDEPLOY): Firebase Admin SDK; консюмеры (группа
  `notification-push`, offset=latest — без шквала пушей на исторических событиях):
  - `charging.user.status`: терминальные статусы → «Зарядка завершена»
    (кВт·ч + сом) / «Сбой зарядки» (Faulted) / «Зарядка прервана»;
  - `booking.state`: `RESERVATION_PROGRESS` с остатком ≤5 мин → «Бронь скоро
    истечёт» (1 раз на бронь); COMPLETED/CANCELLED/PAYMENT_FAILED → терминальные;
  - `payment.topup.events` (НОВЫЙ топик, добавлен в kafka-init): каждое
    зачисленное пополнение → «Кошелёк пополнен» (сумма + баланс).
  Протухшие токены (FCM UNREGISTERED) удаляются через внутренний API.
  Дедуп повторных доставок — bounded LRU по ключу события.
- **payment-service** (REDEPLOY): при каждом успешном пополнении публикует
  `TopUpCompletedEvent{userId, amount, newBalance}` в `payment.topup.events`
  (существующий `payment.events` по-прежнему только при активной брони/зарядке).

> ⚠️ Разделы 12, 18 (websocket-service), 19 (user/notification/payment-service +
> kafka-init) требуют передеплоя. Разделы 13–17 — чисто клиентские.

---

## Приложение: словарь статусов коннектора

| Статус (OCPP) | UI |
|---|---|
| `Available` | Свободен |
| `Preparing`, `Finishing` | Подготовка/завершение (переходные) |
| `Charging`, `SuspendedEV`, `SuspendedEVSE` | Идёт зарядка / приостановлена |
| `Reserved` | Забронирован |
| `Unavailable`, `Faulted` | Недоступен / неисправен |
