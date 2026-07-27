#!/usr/bin/env bash
set -euo pipefail
cd /root/deploy
echo "==> pull das imagens"
docker compose -f docker-compose.prod.yml --env-file .env.prod pull
echo "==> up -d (recria só o que mudou; Flyway roda no boot da api)"
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d
echo "==> limpando imagens órfãs"
docker image prune -f
echo "==> deploy concluído"
