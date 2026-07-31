#!/usr/bin/env bash
# Wrapper para o MCP server do PostgreSQL.
# Extrai as credenciais do .env (formato JDBC) e monta uma URL postgresql:// padrão.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$SCRIPT_DIR/../.env"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "error: .env not found at $ENV_FILE" >&2
  exit 1
fi

read_env_var() {
  grep -E "^${1}=" "$ENV_FILE" | head -1 | sed "s/^${1}=//"
}

DATABASE_URL="$(read_env_var DATABASE_URL)"
DATABASE_USERNAME="$(read_env_var DATABASE_USERNAME)"
DATABASE_PASSWORD="$(read_env_var DATABASE_PASSWORD)"

HOST="$(echo "$DATABASE_URL" | sed 's|jdbc:postgresql://||' | cut -d/ -f1 | cut -d: -f1)"
DB_NAME="$(echo "$DATABASE_URL" | rev | cut -d/ -f1 | rev | cut -d? -f1)"

PG_URL="postgresql://${DATABASE_USERNAME}:${DATABASE_PASSWORD}@${HOST}/${DB_NAME}?sslmode=require"

exec npx -y @modelcontextprotocol/server-postgres "$PG_URL"
