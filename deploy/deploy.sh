#!/usr/bin/env bash
# Deploy de produção do Domus. Instalado como forced-command da chave do GitHub Actions:
# a chave de deploy SÓ consegue executar este script (não abre shell nem roda comando arbitrário).
set -euo pipefail
cd /root/deploy
echo "==> pull das imagens"
docker compose -f docker-compose.prod.yml --env-file .env.prod pull
echo "==> up -d (recria só o que mudou; Flyway roda no boot da api)"
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d
echo "==> limpando imagens órfãs"
docker image prune -f
echo "==> deploy concluído"
