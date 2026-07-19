#!/usr/bin/env bash
#
# Phase 4 — retention логов в Elasticsearch через DATA STREAM (иначе logs-charging растёт бесконечно).
# logs-charging делаем data stream'ом: ILM катит rollover (1д/5ГБ) и удаляет backing-индексы старше 7 дней.
# OTel ES-экспортёр НЕ меняем (пишет в logs-charging create-операцией — проверено, data stream её принимает).
#
# Почему data stream, а не rollover-alias: имя вида logs-charging-000001 попадает под ВСТРОЕННЫЙ шаблон
# ES `logs` (logs-*-*, data-stream-only) → обычный индекс с таким именем создать нельзя. Data stream — родной путь.
#
# ES не опубликован на хост obs-VM — запускать ИЗНУТРИ контейнера elasticsearch:
#   docker cp observability/es-ilm-setup.sh elasticsearch:/tmp/es-ilm-setup.sh
#   docker exec elasticsearch bash /tmp/es-ilm-setup.sh
#
# ГОНКА: gateway-экспортёр непрерывно пишет в logs-charging. Чтобы шаг «удалить старый индекс» не гонялся
# с записью, на время переключения остановите gateway-коллектор (логи ~30с буферизуются в agent на проде):
#   docker stop otel-collector
#   docker exec elasticsearch bash /tmp/es-ilm-setup.sh
#   docker start otel-collector
set -euo pipefail

ES="${ES_ADDR:-http://localhost:9200}"
RETENTION="${RETENTION_DAYS:-7}"

echo "ES = $ES, retention = ${RETENTION}d"

# call <METHOD> <path> [body] — печатает ответ, падает если в ответе "error"
call() {
  local m=$1 p=$2 b=${3:-} out
  if [ -n "$b" ]; then
    out=$(curl -sS -X "$m" "$ES$p" -H 'Content-Type: application/json' -d "$b")
  else
    out=$(curl -sS -X "$m" "$ES$p")
  fi
  if printf '%s' "$out" | grep -q '"error"'; then
    echo "   ERROR: $out"; return 1
  fi
  echo "   ok"
}

# is_ds — истина, ТОЛЬКО если logs-charging реально data stream.
# (GET /_data_stream/<name> отдаёт 200 с "data_streams":[] и для не-существующего — на код полагаться нельзя.)
is_ds() { curl -sS "$ES/_data_stream/logs-charging" | grep -q '"name":"logs-charging"'; }

echo "1) ILM policy logs-charging-policy (rollover 1д/5ГБ + delete >${RETENTION}d)"
call PUT "/_ilm/policy/logs-charging-policy" '{
  "policy": { "phases": {
    "hot":    { "actions": { "rollover": { "max_age": "1d", "max_primary_shard_size": "5gb" } } },
    "delete": { "min_age": "'"${RETENTION}"'d", "actions": { "delete": {} } }
  } }
}'

echo "2) index template logs-charging (DATA STREAM, priority 500)"
call PUT "/_index_template/logs-charging" '{
  "index_patterns": ["logs-charging"],
  "data_stream": {},
  "priority": 500,
  "template": {
    "settings": {
      "index.number_of_replicas": 0,
      "index.lifecycle.name": "logs-charging-policy"
    },
    "mappings": { "properties": { "@timestamp": { "type": "date" } } }
  }
}'

echo "3) если logs-charging — старый обычный индекс, удаляем (освобождаем имя под data stream)"
if is_ds; then
  echo "   logs-charging уже data stream — удаление не требуется"
else
  curl -sS -X DELETE "$ES/logs-charging" >/dev/null 2>&1 || true
  echo "   ok (обычный индекс logs-charging удалён, если существовал)"
fi

echo "4) создаём data stream logs-charging"
if is_ds; then
  echo "   уже существует"
else
  call PUT "/_data_stream/logs-charging"
fi

echo
echo "Готово. Проверка:"
echo "  curl -s $ES/_data_stream/logs-charging?pretty      # тип и ILM"
echo "  curl -s $ES/_cat/indices/.ds-logs-charging*?v      # backing-индексы"
echo "  curl -s $ES/logs-charging/_count                   # растёт после старта коллектора"
