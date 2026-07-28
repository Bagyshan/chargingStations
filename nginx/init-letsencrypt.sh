#!/usr/bin/env bash
# ============================================================================
# Первый выпуск TLS-сертификата Let's Encrypt для nginx (#6). Идемпотентно.
# Запускать ОДИН РАЗ при переходе на HTTPS (потом продлевает сам сервис certbot).
#
# Предусловия:
#   1) Домен (A-запись) указывает на ЭТОТ сервер.
#   2) Порт 80 открыт из интернета (Let's Encrypt проверяет HTTP-01 challenge).
#   3) В .env заданы SERVER_HOST=<домен> и LETSENCRYPT_EMAIL=<почта>.
#
# Использование (из корня проекта):
#   ./nginx/init-letsencrypt.sh
#   # тест без лимитов LE (рекомендуется первый раз): STAGING=1 ./nginx/init-letsencrypt.sh
#
# После успеха: выставить PUBLIC_SCHEME=https в .env и передеплоить приложения
# (issuer Keycloak и ссылки станут https). См. SECURITY_TASKS.md.
#
# В Kubernetes этот скрипт НЕ нужен — сертификат выпускает cert-manager (ClusterIssuer + Certificate).
# ============================================================================
set -euo pipefail
cd "$(dirname "$0")/.."   # корень проекта

# Подхватываем .env, если есть (для SERVER_HOST / LETSENCRYPT_EMAIL)
[ -f .env ] && set -a && . ./.env && set +a

COMPOSE="docker compose -f docker-compose.prod.yaml"
CERT_NAME="charging"
DOMAIN="${DOMAIN:-${SERVER_HOST:-}}"
EMAIL="${LETSENCRYPT_EMAIL:-}"
STAGING="${STAGING:-0}"
LIVE="/etc/letsencrypt/live/${CERT_NAME}"

[ -n "$DOMAIN" ] && [ "$DOMAIN" != "localhost" ] || { echo "ERROR: задай SERVER_HOST=<домен> (не localhost)"; exit 1; }
[ -n "$EMAIL" ] || { echo "ERROR: задай LETSENCRYPT_EMAIL=<почта> в .env"; exit 1; }

echo "Домен: $DOMAIN | email: $EMAIL | staging: $STAGING"

echo "1/5 Временный self-signed сертификат (чтобы nginx :443 смог стартовать)…"
$COMPOSE run --rm --entrypoint sh certbot -c \
  "mkdir -p $LIVE && openssl req -x509 -nodes -newkey rsa:2048 -days 1 \
     -keyout $LIVE/privkey.pem -out $LIVE/fullchain.pem -subj '/CN=localhost'"

echo "2/5 Поднимаем nginx с временным сертификатом…"
$COMPOSE up -d --force-recreate nginx

echo "3/5 Удаляем временный сертификат (освобождаем имя '$CERT_NAME')…"
$COMPOSE run --rm --entrypoint sh certbot -c \
  "rm -Rf /etc/letsencrypt/live/$CERT_NAME /etc/letsencrypt/archive/$CERT_NAME /etc/letsencrypt/renewal/$CERT_NAME.conf"

echo "4/5 Запрашиваем боевой сертификат Let's Encrypt (webroot HTTP-01)…"
STAGING_ARG=""; [ "$STAGING" = "1" ] && STAGING_ARG="--staging"
$COMPOSE run --rm --entrypoint certbot certbot \
  certonly --webroot -w /var/www/certbot --cert-name "$CERT_NAME" \
    -d "$DOMAIN" --email "$EMAIL" --agree-tos --no-eff-email --non-interactive $STAGING_ARG

echo "5/5 Перезагружаем nginx с боевым сертификатом…"
$COMPOSE exec nginx nginx -s reload

echo
echo "✅ Готово. Проверь: https://$DOMAIN (http должен редиректить на https)."
echo "   Дальше: PUBLIC_SCHEME=https в .env → передеплой приложений (issuer Keycloak станет https)."
