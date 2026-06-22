#!/usr/bin/env bash
# Сборка всех сервисов и запуск полного стека (docker-compose.prod.yaml).
# Использование:
#   ./deploy.sh            # собрать jar + поднять стек
#   ./deploy.sh --no-build # пропустить сборку jar (только docker compose up)
set -euo pipefail

cd "$(dirname "$0")"

COMPOSE_FILE="docker-compose.prod.yaml"

# Maven wrapper (mvnw) требует JAVA_HOME. Если он не выставлен в окружении,
# определяем его по реальному пути к java на PATH.
if [ -z "${JAVA_HOME:-}" ] || [ ! -x "${JAVA_HOME:-}/bin/java" ]; then
  if command -v java >/dev/null 2>&1; then
    JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"
    export JAVA_HOME
    echo ">>> JAVA_HOME не задан — использую $JAVA_HOME"
  else
    echo "ОШИБКА: не найден java. Установи JDK 21 или задай JAVA_HOME." >&2
    exit 1
  fi
fi

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
