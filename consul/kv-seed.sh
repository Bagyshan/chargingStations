#!/usr/bin/env bash
#
# Сид Consul KV значениями по умолчанию (JWK + таймауты). Идемпотентно (PUT перезаписывает).
# То же самое делает контейнер consul-kv-init в docker-compose; этот скрипт — для ручного
# запуска и для «живого» изменения значений (hot-reload).
#
# Примеры:
#   ./consul/kv-seed.sh                                  # localhost:8500, JWK=localhost
#   CONSUL_HTTP_ADDR=http://1.2.3.4:8500 ./consul/kv-seed.sh
#   JWK_SET_URI=http://keycloak:8080/realms/charging-stations/protocol/openid-connect/certs ./consul/kv-seed.sh
#
set -euo pipefail

ADDR="${CONSUL_HTTP_ADDR:-http://localhost:8500}"
JWK="${JWK_SET_URI:-http://localhost:8080/realms/charging-stations/protocol/openid-connect/certs}"

put() {  # put <key> <value>
  curl -fsS -X PUT "$ADDR/v1/kv/$1" --data-binary "$2" >/dev/null && echo "  ✔ $1 = $2"
}

echo "Seeding Consul KV @ $ADDR"
# Общий для всех resource-server'ов JWK-URI (config/application/ читают ВСЕ сервисы).
put "config/application/app.security.jwk-set-uri"                       "$JWK"
# station-controll-service: порог offline-свипа станций, сек.
put "config/station-controll-service/station.offline-threshold-seconds" "21600"
# state-updater-service: клиент к station-controll по имени (lb://) + таймауты, мс.
put "config/state-updater-service/app.services.station-controll.base-url"           "lb://station-controll-service"
put "config/state-updater-service/app.services.station-controll.connect-timeout-ms" "5000"
put "config/state-updater-service/app.services.station-controll.read-timeout-ms"    "30000"
echo "Done."
