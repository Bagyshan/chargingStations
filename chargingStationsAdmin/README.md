# BatEnergy · Админ-панель

Веб-панель управления платформой зарядных станций (роли **ADMIN / SPECIALIST / CONTRACTOR**).
Фаза 1 — фундамент проекта с продуманным дизайном и мок-данными; доменные модели уже
соответствуют DTO бэкенда, поэтому переход на реальный API в Фазе 2 не потребует правок типов.

## Стек

- **React 19 + TypeScript + Vite 6**
- **Tailwind CSS v4** — дизайн-токены, тёмная/светлая тема
- **TanStack Router** — типобезопасная маршрутизация с ролевыми guard'ами
- **TanStack Query** — серверное состояние (сейчас поверх мок-слоя)
- **Zustand** — авторизация/роль и тема
- **Recharts** — графики
- **lucide-react** — иконки
- Собственная UI-библиотека в стиле shadcn (`src/components/ui`)

## Запуск

```bash
npm install
npm run dev      # http://localhost:5273
npm run build    # прод-сборка (tsc + vite)
npm run preview
```

## Демо-доступ

На экране входа — вход в один клик под любой ролью:

| Роль | Email | Пароль |
|---|---|---|
| Администратор | `admin@batenergy.kg` | `admin` |
| Специалист | `specialist@batenergy.kg` | `spec` |
| Контрагент | `operator@batenergy.kg` | `operator` |

Роль можно переключать «на лету» из шапки (демо-режим). Контрагент видит только свои станции,
раздел «Пользователи» доступен только администратору.

## Структура

```
src/
  app/router.tsx        маршруты + guard'ы
  api/hooks.ts          хуки данных (TanStack Query) поверх мок-слоя
  mock/                 сид-данные и мок-API (форма = DTO бэкенда)
  store/                auth (роль/скоуп) и theme
  types/domain.ts       доменные модели (зеркало DTO)
  components/           UI-кит, статусы, таблица, графики, layout
  features/             страницы: dashboard, stations, connectors, bookings,
                        transactions, analytics, users, tariffs, connector-types, settings
```

## Соответствие бэкенду

| Раздел | Реальный эндпоинт (Фаза 2, через api-gateway) | Роли |
|---|---|---|
| Станции | `/contractor-admin/api/charge-boxes` | ADMIN·SPEC·CONTRACTOR |
| Коннекторы | `/contractor-admin/api/connectors` | те же |
| Брони | `/contractor-admin/api/bookings` | те же (чтение) |
| Зарядки | `/contractor-admin/api/transactions` | те же (чтение) |
| Аналитика | `/contractor-admin/api/analytics/{energy,revenue,bookings}` | те же |
| Тарифы | `/contractor-admin/api/stations/{id}/hourly-tariffs` | те же |
| Типы коннекторов | `/station-controll/api/connector-types` | ADMIN·SPEC |
| Пользователи | `/user/api/v1/admin/users` | ADMIN |
| Баланс (пополнение) | `/payment/api/v1/admin/balance/*` | ADMIN |

## Дальнейшие фазы

- **Фаза 2** — реальный API-шлюз + авторизация Keycloak (JWT, realm-роли), замена мок-слоя.
- **Фаза 3** — формы CRUD с валидацией, детальные экраны станций, drawer'ы.
- **Фаза 4** — расширенная аналитика, экспорт, realtime по WebSocket.
