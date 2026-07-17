#!/usr/bin/env bash
#
# Скачивает OpenTelemetry Java-агент — им в Фазе 3 инструментируются ВСЕ
# JVM-сервисы (одна строка -javaagent в общем якоре x-java-opts прод-compose,
# включая station-steve). Здесь просто стейджим jar; в образы он попадёт позже.
#
#   ./observability/download-otel-agent.sh                 # версия по умолчанию
#   OTEL_AGENT_VERSION=2.6.0 ./observability/download-otel-agent.sh
#
set -euo pipefail

VERSION="${OTEL_AGENT_VERSION:-2.6.0}"
DEST_DIR="$(cd "$(dirname "$0")" && pwd)/agent"
DEST="$DEST_DIR/opentelemetry-javaagent.jar"
URL="https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${VERSION}/opentelemetry-javaagent.jar"

mkdir -p "$DEST_DIR"
echo "Downloading OTel Java agent v$VERSION"
echo "  $URL"
curl -fSL "$URL" -o "$DEST"
echo "Saved: $DEST"
java -jar "$DEST" 2>/dev/null || true   # печатает версию агента, если java есть
echo "OK"
