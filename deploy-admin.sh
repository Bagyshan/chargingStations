#!/usr/bin/env bash
# Сборка и деплой веб-админки (chargingStationsAdmin) как статики за nginx на /console/.
#
# Что делает:
#   1) npm ci + npm run build  → chargingStationsAdmin/dist  (baked env: .env.production, api-режим)
#   2) поднимает/пересоздаёт контейнер nginx, чтобы он подхватил bind-mount ./chargingStationsAdmin/dist
#
# Использование:
#   ./deploy-admin.sh              # собрать dist + применить в nginx
#   ./deploy-admin.sh --no-build   # только применить в nginx (dist уже собран)
#   ./deploy-admin.sh --reload     # dist уже смонтирован, просто nginx -s reload (для статики даже не нужно)
#
# ВАЖНО: статику nginx читает с диска при каждом запросе. После первого деплоя
# (когда volume уже смонтирован) для обновления панели достаточно `npm run build` —
# новые файлы отдаются сразу, пересоздавать/релоадить nginx НЕ требуется.
set -euo pipefail

cd "$(dirname "$0")"

COMPOSE_FILE="docker-compose.prod.yaml"
ADMIN_DIR="chargingStationsAdmin"

case "${1:-}" in
  --reload)
    echo ">>> nginx -s reload"
    docker compose -f "$COMPOSE_FILE" exec nginx nginx -s reload
    exit 0
    ;;
  --no-build)
    SKIP_BUILD=1
    ;;
  "")
    SKIP_BUILD=0
    ;;
  *)
    echo "Неизвестный аргумент: $1" >&2
    exit 1
    ;;
esac

if [ "$SKIP_BUILD" -eq 0 ]; then
  if ! command -v npm >/dev/null 2>&1; then
    echo "ОШИБКА: не найден npm. Установи Node.js 20+ (или собери dist на другой машине и залей)." >&2
    exit 1
  fi
  echo ">>> Сборка админки ($ADMIN_DIR) в api-режиме (.env.production)"
  ( cd "$ADMIN_DIR" && npm ci && npm run build )
fi

if [ ! -f "$ADMIN_DIR/dist/index.html" ]; then
  echo "ОШИБКА: нет $ADMIN_DIR/dist/index.html — сначала собери (запусти без --no-build)." >&2
  exit 1
fi

echo ">>> Применяю в nginx (пересоздаю контейнер, чтобы подхватить volume со статикой)"
docker compose -f "$COMPOSE_FILE" up -d nginx

echo ">>> Готово. Админка: http://<host>/console/"
