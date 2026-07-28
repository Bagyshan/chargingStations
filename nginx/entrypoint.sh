#!/bin/sh
# Обёртка запуска nginx для TLS (#6):
#  1) Если боевого сертификата ещё нет — генерим ВРЕМЕННЫЙ self-signed, чтобы nginx :443 поднялся
#     (иначе nginx падает без ssl_certificate и роняет весь сайт). Боевой серт выпускает
#     nginx/init-letsencrypt.sh (Let's Encrypt), продлевает сервис certbot.
#  2) Периодический `nginx -s reload` раз в 6ч — подхватить продлённый certbot'ом серт.
#
# В Kubernetes эта обёртка НЕ нужна: сертификатом заведует cert-manager, reload делает он же.
set -e

CERT_DIR=/etc/letsencrypt/live/charging

if [ ! -f "$CERT_DIR/fullchain.pem" ]; then
  echo "[nginx-entrypoint] Боевого TLS-сертификата нет — генерирую временный self-signed…"
  apk add --no-cache openssl >/dev/null 2>&1 || true
  mkdir -p "$CERT_DIR"
  if openssl req -x509 -nodes -newkey rsa:2048 -days 3650 \
        -keyout "$CERT_DIR/privkey.pem" -out "$CERT_DIR/fullchain.pem" \
        -subj '/CN=localhost' >/dev/null 2>&1; then
    echo "[nginx-entrypoint] Временный self-signed создан. Выпусти боевой: ./nginx/init-letsencrypt.sh"
  else
    echo "[nginx-entrypoint] WARN: openssl недоступен (нет сети?). nginx :443 не стартует без серта —"
    echo "[nginx-entrypoint]       запусти ./nginx/init-letsencrypt.sh, он создаст серт через контейнер certbot."
  fi
fi

# Фоновый reload под обновлённый сертификат.
( while :; do sleep 6h & wait ${!}; nginx -s reload 2>/dev/null || true; done ) &

exec nginx -g 'daemon off;'
