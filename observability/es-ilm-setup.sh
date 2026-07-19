#!/usr/bin/env bash
#
# Phase 4 — retention логов в Elasticsearch (иначе индекс logs-charging растёт бесконечно).
# Делает logs-charging управляемым по ILM: rollover (1 день / 5 ГБ) + удаление старше 7 дней.
#
# Схема: OTel ES-экспортёр пишет в АЛИАС logs-charging -> он указывает на logs-charging-000001,
# ILM катит rollover в -000002, -000003... и удаляет куски старше 7д. Экспортёр менять не нужно
# (в gateway остаётся logs_index: logs-charging — теперь это write-alias).
#
# Запуск ОДИН РАЗ на obs-VM:
#   ./observability/es-ilm-setup.sh
#   # или удалённо: ES_ADDR=http://obs-vm:9200 ./observability/es-ilm-setup.sh
#
# ВНИМАНИЕ: если logs-charging уже существует как ОБЫЧНЫЙ индекс (его создал экспортёр),
# скрипт его удалит (немного накопленных логов потеряется) и пересоздаст как alias.
set -euo pipefail

ES="${ES_ADDR:-http://localhost:9200}"
RETENTION="${RETENTION_DAYS:-7}"
J='-H Content-Type:application/json'

echo "ES = $ES, retention = ${RETENTION}d"

echo "1) ILM policy logs-charging-policy"
curl -fsS $J -X PUT "$ES/_ilm/policy/logs-charging-policy" -d @- >/dev/null <<JSON
{
  "policy": {
    "phases": {
      "hot":    { "actions": { "rollover": { "max_age": "1d", "max_primary_shard_size": "5gb" } } },
      "delete": { "min_age": "${RETENTION}d", "actions": { "delete": {} } }
    }
  }
}
JSON
echo "   ok"

echo "2) index template logs-charging (pattern logs-charging-*)"
curl -fsS $J -X PUT "$ES/_index_template/logs-charging" -d @- >/dev/null <<'JSON'
{
  "index_patterns": ["logs-charging-*"],
  "template": {
    "settings": {
      "number_of_replicas": 0,
      "index.lifecycle.name": "logs-charging-policy",
      "index.lifecycle.rollover_alias": "logs-charging"
    },
    "mappings": {
      "properties": {
        "@timestamp": { "type": "date" }
      }
    }
  }
}
JSON
echo "   ok"

echo "3) если logs-charging — обычный индекс, удаляем (освобождаем имя под alias)"
if curl -fsS "$ES/logs-charging" -o /dev/null 2>/dev/null; then
  # это индекс, а не alias -> удалить
  if curl -fsS "$ES/_alias/logs-charging" -o /dev/null 2>/dev/null; then
    echo "   logs-charging уже alias — пропускаем удаление"
  else
    curl -fsS -X DELETE "$ES/logs-charging" >/dev/null && echo "   удалён старый индекс logs-charging"
  fi
fi

echo "4) bootstrap первого индекса с write-alias logs-charging"
if curl -fsS "$ES/logs-charging-000001" -o /dev/null 2>/dev/null; then
  echo "   logs-charging-000001 уже есть — пропускаем"
else
  curl -fsS $J -X PUT "$ES/logs-charging-000001" -d @- >/dev/null <<'JSON'
{ "aliases": { "logs-charging": { "is_write_index": true } } }
JSON
  echo "   создан logs-charging-000001 + alias logs-charging(write)"
fi

echo
echo "Готово. Проверка:"
echo "  curl -s $ES/_cat/indices/logs-charging*?v"
echo "  curl -s $ES/_ilm/policy/logs-charging-policy"
