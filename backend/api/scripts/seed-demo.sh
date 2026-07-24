#!/usr/bin/env bash
# Popula o Postgres local com os dados de demonstração do TCC.
#
# Pré-requisito: a API já subiu uma vez contra este banco (Flyway cria o schema).
# Uso: ./scripts/seed-demo.sh
set -euo pipefail

CONTAINER="${CONTAINER:-domus-db-dev}"
DB="${DB:-domus}"
USER="${USER_DB:-domus}"
SQL="$(dirname "$0")/seed-demo.sql"

if ! docker ps --format '{{.Names}}' | grep -qx "$CONTAINER"; then
  echo "Container '$CONTAINER' não está no ar. Suba com:"
  echo "  docker compose -f docker-compose.dev.yml up -d postgres"
  exit 1
fi

if ! docker exec "$CONTAINER" psql -U "$USER" -d "$DB" -tAc \
      "SELECT to_regclass('public.membro')" | grep -q membro; then
  echo "O schema ainda não existe neste banco."
  echo "Suba a API uma vez para o Flyway rodar as migrations, depois rode este script de novo."
  exit 1
fi

echo "Populando '$DB' em $CONTAINER..."
docker exec -i "$CONTAINER" psql -U "$USER" -d "$DB" -v ON_ERROR_STOP=1 < "$SQL"

cat <<'EOF'

Seed concluído.

Login (qualquer um dos três, senha: domus123):
  admin@domus.dev    ADMIN_IGREJA
  lider@domus.dev    LIDER
  membro@domus.dev   MEMBRO

A indexação no Elasticsearch acontece sozinha: o OutboxProcessador consome a
fila a cada 3s enquanto a API estiver rodando. Para conferir:
  docker exec domus-db-dev psql -U domus -d domus -c \
    "SELECT processado, count(*) FROM outbox GROUP BY 1"
EOF
