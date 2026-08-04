#!/usr/bin/env bash
# =============================================================================
# Быстрая ПРЕ-сборка station-steve (SteVe).
#
# Зачем: раньше SteVe собирался Maven'ом ВНУТРИ контейнера при КАЖДОМ старте
# (CMD = `./mvnw clean package && java -jar`), из-за чего рестарт занимал минуты.
# Теперь jar собирается заранее этим скриптом, а контейнер station-steve просто
# запускает готовый target/steve.jar (см. `command:` у сервиса `app` в
# docker-compose.prod.yaml) — старт занимает секунды.
#
# Почему в контейнере, а не на хосте: SteVe использует jOOQ-кодген + Flyway,
# которые во время СБОРКИ подключаются к ЖИВОЙ MariaDB (jdbc:mysql://mariadb:3306).
# Хост `mariadb` резолвится только внутри docker-сети, поэтому билд идёт в
# одноразовом JDK17-контейнере на той же сети, а jar кладётся в
# ./station-steve/target/steve.jar через bind-mount.
#
# Использование:
#   ./build-steve.sh             # собрать steve.jar (поднимет MariaDB, если не запущена)
#   ./build-steve.sh --restart   # собрать + быстро перезапустить контейнер station-steve
#
# Первый билд качает зависимости (~минуты); дальше кэш ~/.m2 (том steve-m2-cache)
# делает пересборку быстрой.
# =============================================================================
set -euo pipefail
cd "$(dirname "$0")"

COMPOSE_FILE="docker-compose.prod.yaml"
DB_CONTAINER="steve-db"
M2_VOLUME="steve-m2-cache"
BUILDER_IMAGE="eclipse-temurin:17-jdk"

if [ ! -f .env ]; then
  echo "ОШИБКА: нет файла .env (нужен для запуска MariaDB). Создай:  cp .env.example .env" >&2
  exit 1
fi

echo ">>> 1/4 Поднимаю MariaDB (нужна для jOOQ-кодгена во время сборки)"
docker compose -f "$COMPOSE_FILE" up -d "$DB_CONTAINER" >/dev/null 2>&1 || \
  docker compose -f "$COMPOSE_FILE" up -d db

echo ">>> 2/4 Жду готовности MariaDB ($DB_CONTAINER)"
ready=0
for i in $(seq 1 60); do
  if docker exec "$DB_CONTAINER" mysqladmin ping --silent >/dev/null 2>&1; then
    echo "    MariaDB готова (через $((i*2))с)"; ready=1; break
  fi
  sleep 2
done
if [ "$ready" != 1 ]; then
  echo "ОШИБКА: MariaDB не поднялась за 120с. Проверь: docker logs $DB_CONTAINER" >&2
  exit 1
fi

# Сеть, на которой реально сидит steve-db — к ней подключаем билд-контейнер,
# чтобы резолвился алиас `mariadb` (не хардкодим имя: оно зависит от префикса проекта).
NET=$(docker inspect -f '{{range $k,$v := .NetworkSettings.Networks}}{{$k}} {{end}}' "$DB_CONTAINER" | awk '{print $1}')
if [ -z "$NET" ]; then
  echo "ОШИБКА: не удалось определить docker-сеть контейнера $DB_CONTAINER" >&2
  exit 1
fi
echo "    Сеть сборки: $NET"

echo ">>> 3/4 Собираю steve.jar в одноразовом контейнере $BUILDER_IMAGE"
docker volume create "$M2_VOLUME" >/dev/null
docker run --rm \
  --network "$NET" \
  -v "$PWD/station-steve:/code" \
  -v "$M2_VOLUME:/root/.m2" \
  -w /code \
  "$BUILDER_IMAGE" \
  ./mvnw -B clean package -Pdocker -Djdk.tls.client.protocols="TLSv1,TLSv1.1,TLSv1.2"

echo ">>> 4/4 Готово:"
ls -lh station-steve/target/steve.jar

if [ "${1:-}" = "--restart" ]; then
  echo ">>> Перезапускаю контейнер station-steve (подхватит новый jar за секунды)"
  docker compose -f "$COMPOSE_FILE" up -d --force-recreate app
  echo ">>> Смотреть логи:  docker logs -f station-steve"
else
  echo ">>> jar собран. Чтобы применить — перезапусти контейнер:"
  echo "      docker compose -f $COMPOSE_FILE up -d --force-recreate app"
  echo "    (или запусти этот скрипт с флагом --restart)"
fi
