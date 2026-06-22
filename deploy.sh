#!/usr/bin/env bash
# Сборка всех сервисов и запуск полного стека (docker-compose.prod.yaml).
# Использование:
#   ./deploy.sh            # собрать jar + поднять стек
#   ./deploy.sh --no-build # пропустить сборку jar (только docker compose up)
set -euo pipefail

cd "$(dirname "$0")"

COMPOSE_FILE="docker-compose.prod.yaml"

if [ ! -f .env ]; then
  echo "ОШИБКА: нет файла .env. Создай его:  cp .env.example .env  и заполни значения." >&2
  exit 1
fi

if [ "${1:-}" != "--no-build" ]; then
  echo ">>> Сборка jar всех модулей (кроме station-steve — он собирается в своём контейнере)"
  # В корне нет mvnw — запускаем wrapper любого модуля против родительского pom.xml.
  ./booking-service/mvnw -f pom.xml clean package -Dmaven.test.skip=true -pl '!:steve'
fi

echo ">>> Сборка образов и запуск стека"
docker compose -f "$COMPOSE_FILE" up -d --build

echo ">>> Готово. Статус:"
docker compose -f "$COMPOSE_FILE" ps
