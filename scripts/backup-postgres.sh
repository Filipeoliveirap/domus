#!/usr/bin/env bash
set -Eeuo pipefail

DRY_RUN=false
[[ "${1:-}" == "--dry-run" ]] && DRY_RUN=true

: "${BACKUP_DATABASE_URL:?defina BACKUP_DATABASE_URL (formato libpq, NAO jdbc)}"
if [[ "$DRY_RUN" == false ]]; then
  : "${AGE_PUBLIC_KEY:?defina AGE_PUBLIC_KEY}"
  : "${R2_ENDPOINT:?defina R2_ENDPOINT}"
  : "${R2_BUCKET:?defina R2_BUCKET}"
  : "${AWS_ACCESS_KEY_ID:?defina AWS_ACCESS_KEY_ID}"
  : "${AWS_SECRET_ACCESS_KEY:?defina AWS_SECRET_ACCESS_KEY}"
fi

PG_IMAGE="postgres:16"
AWS_IMAGE="amazon/aws-cli:2.36.1"

TS="$(date -u +%Y%m%dT%H%M%SZ)"
WORKDIR="$(mktemp -d)"
DUMP="$WORKDIR/domus-${TS}.dump"
CONTAINER="domus-restore-teste-$$"

limpar() {
  docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
  rm -rf "$WORKDIR"
}
trap limpar EXIT
SQL_CONTAGENS="
SELECT tablename || '=' ||
       (xpath('/row/c/text()',
              query_to_xml(format('select count(*) as c from public.%I', tablename),
                           false, true, '')))[1]::text
FROM pg_tables
WHERE schemaname = 'public'
ORDER BY tablename;
"

echo "==> 1/4 Dump do Neon (pg_dump -Fc)"
docker run --rm -e PGURL="$BACKUP_DATABASE_URL" "$PG_IMAGE" \
  sh -c 'pg_dump "$PGURL&connect_timeout=30" -Fc --no-owner --no-privileges' > "$DUMP"

TAMANHO=$(stat -c%s "$DUMP")
echo "    dump: ${TAMANHO} bytes"
if [[ "$TAMANHO" -lt 1024 ]]; then
  echo "ERRO: dump menor que 1 KB — algo deu muito errado." >&2
  exit 1
fi

echo "==> 2/4 Contagens na origem"
docker run --rm -e PGURL="$BACKUP_DATABASE_URL" -e SQL="$SQL_CONTAGENS" "$PG_IMAGE" \
  sh -c 'psql "$PGURL" -tAc "$SQL"' | grep -v '^[[:space:]]*$' | sort > "$WORKDIR/origem.txt"
echo "    $(wc -l < "$WORKDIR/origem.txt") tabelas:"
sed 's/^/      /' "$WORKDIR/origem.txt"

echo "==> 3/4 Teste de restauração num Postgres descartável"
docker run -d --name "$CONTAINER" \
  -e POSTGRES_PASSWORD=teste -e POSTGRES_DB=restore_test "$PG_IMAGE" >/dev/null
for _ in $(seq 1 30); do
  docker exec "$CONTAINER" pg_isready -U postgres -q 2>/dev/null && break
  sleep 1
done
docker exec "$CONTAINER" pg_isready -U postgres -q \
  || { echo "ERRO: o Postgres de teste não subiu." >&2; exit 1; }

docker cp "$DUMP" "$CONTAINER:/tmp/d.dump"
docker exec "$CONTAINER" pg_restore -U postgres -d restore_test \
  --no-owner --no-privileges /tmp/d.dump

docker exec -e SQL="$SQL_CONTAGENS" "$CONTAINER" \
  sh -c 'psql -U postgres -d restore_test -tAc "$SQL"' \
  | grep -v '^[[:space:]]*$' | sort > "$WORKDIR/restaurado.txt"

echo "    comparando contagens origem x restaurado..."
if ! diff -u "$WORKDIR/origem.txt" "$WORKDIR/restaurado.txt"; then
  echo "ERRO: as contagens divergem. O dump está incompleto ou corrompido." >&2
  echo "      (- = Neon, + = restaurado)" >&2
  exit 1
fi
echo "    OK: todas as tabelas bateram."

if [[ "$DRY_RUN" == true ]]; then
  echo "==> dry-run: parando aqui. Nada foi criptografado nem enviado."
  exit 0
fi

echo "==> 4/4 Criptografando e enviando"
age -r "$AGE_PUBLIC_KEY" -o "${DUMP}.age" "$DUMP"

TAM_CIFRADO=$(stat -c%s "${DUMP}.age")
echo "    criptografado: ${TAM_CIFRADO} bytes"

if ! head -c 21 "${DUMP}.age" | grep -q "age-encryption.org"; then
  echo "ERRO: arquivo criptografado sem o cabeçalho do age." >&2
  exit 1
fi

docker run --rm \
  -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY -e AWS_DEFAULT_REGION=auto \
  -v "${WORKDIR}:/w:ro" \
  "$AWS_IMAGE" \
  s3 cp "/w/$(basename "${DUMP}.age")" "s3://${R2_BUCKET}/domus-${TS}.dump.age" \
  --endpoint-url "$R2_ENDPOINT" \
  --only-show-errors

echo "    enviado: s3://${R2_BUCKET}/domus-${TS}.dump.age"

echo "==> Conferindo no R2 que o objeto realmente existe"
TAM_REMOTO=$(docker run --rm \
  -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY -e AWS_DEFAULT_REGION=auto \
  "$AWS_IMAGE" \
  s3api head-object --bucket "$R2_BUCKET" --key "domus-${TS}.dump.age" \
  --endpoint-url "$R2_ENDPOINT" --query ContentLength --output text 2>&1) || {
    echo "ERRO: o upload retornou sucesso, mas o objeto NÃO está no bucket." >&2
    echo "      resposta do R2: $TAM_REMOTO" >&2
    exit 1
  }

if [[ "$TAM_REMOTO" != "$TAM_CIFRADO" ]]; then
  echo "ERRO: o objeto no bucket tem ${TAM_REMOTO} bytes, mas o arquivo enviado tinha ${TAM_CIFRADO}." >&2
  exit 1
fi
echo "    confirmado: ${TAM_REMOTO} bytes no bucket (bate com o enviado)"

echo "==> Backup concluído."
