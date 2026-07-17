# Наблюдаемость (metrics / logs / traces)

Самодостаточный стек, живёт на **отдельном obs-VM** (не на прод-хосте). Прод шлёт всё
асинхронно через локальный OTel Collector-agent в **единую воронку** — gateway Collector
этого VM, который разводит по бэкендам:

```
прод-host (сервисы+agent) ──OTLP:4317──►  gateway OTel Collector  ──► Tempo        (трейсы)
                                                                   ──► Prometheus   (метрики)
                                                                   ──► Elasticsearch(логи) ──► Kibana / Grafana
```

- **Grafana** (`:3000`) — единая панель: метрики + трейсы + логи, корреляция по `trace_id`.
- **Kibana** (`:5601`) — глубокий поиск логов.

## Фаза 0 — поднять стек на obs-VM

```bash
# на obs-VM, из корня репозитория:
GF_ADMIN_PASSWORD='<секрет>' docker compose -f docker-compose.observability.yaml up -d
docker compose -f docker-compose.observability.yaml ps
```

Проверка:
- Grafana → `http://<obs-vm>:3000` (admin / <секрет>). Datasources Prometheus/Tempo/Elasticsearch
  провижнятся автоматически (Connections → Data sources — три зелёных).
- Kibana → `http://<obs-vm>:5601`.
- Приём OTLP жив: `nc -vz <obs-vm> 4317` с прод-хоста.

## Порты, которые должны быть открыты на obs-VM

| Порт | Кто ходит | Firewall |
|---|---|---|
| `4317` / `4318` | прод-host (OTLP) | **только IP прод-хоста** |
| `3000` (Grafana) | админ | VPN / твой IP |
| `5601` (Kibana) | админ | VPN / твой IP |

`9200` (ES), `9090` (Prometheus), `3200` (Tempo) наружу **не** открываются — только внутри
сети VM; Grafana проксирует их сама.

## Файлы

| Файл | Что |
|---|---|
| `docker-compose.observability.yaml` | стек obs-VM (Collector, Tempo, Prometheus, ES, Kibana, Grafana) |
| `observability/otel-collector-gateway.yaml` | конфиг gateway-Collector'а (3 пайплайна) |
| `observability/prometheus.yml` | Prometheus (remote-write приём + self-scrape) |
| `observability/tempo.yaml` | Tempo (OTLP-приём, retention 7д, service-graph/span-metrics) |
| `observability/grafana/provisioning/datasources/datasources.yaml` | 3 datasource + корреляция trace↔log |
| `observability/download-otel-agent.sh` | стейдж OTel Java-агента (для Фазы 3) |

## Дальше

- **Фаза 1** — метрики: включить `prometheus` actuator у всех сервисов + прод-agent (scrape→remote-write).
- **Фаза 2** — логи: структурный JSON (Boot 3.4 ECS) + filelog в прод-agent → ES.
- **Фаза 3** — трейсы: `-javaagent` в `x-java-opts`, прод-agent OTLP → gateway. Полный путь через Kafka/auth.
- **Фаза 4** — дашборды, алерты, retention/ILM.
