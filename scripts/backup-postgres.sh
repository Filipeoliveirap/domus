#!/usr/bin/env bash
#
# Backup do Postgres do Domus (Neon).
#
# Ordem inegociável: dump -> testa em claro -> criptografa -> sobe.
# O teste vem ANTES da criptografia porque só a chave PÚBLICA existe aqui:
# este script consegue criptografar e NÃO consegue descriptografar. É o ponto.
#
# Uso:
#   ./scripts/backup-postgres.sh            # completo
#   ./scripts/backup-postgres.sh --dry-run  # só dump + teste (não sobe nada)
#
# Requer: docker (e age, no modo completo).
# pg_dump/pg_restore/psql e o AWS CLI rodam via Docker com versão fixa, para
# que a máquina local e o CI executem exatamente o mesmo.

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

# Versão do Postgres: DEVE ser >= a do servidor. O Neon roda 16.14 (verificado
# em 2026-07-17). Um pg_dump mais novo dumpa servidor mais antigo; o contrário
# quebra. Usamos 16 (e não 17) para o teste restaurar na MESMA versão da
# produção — num desastre real é num Neon 16 que o dump vai entrar.
PG_IMAGE="postgres:16"
# Versão cravada, não "latest": latest muda sozinho e um dia o backup quebraria
# às 3 da manhã porque a AWS publicou uma versão nova. (E não existe tag "2" —
# o amazon/aws-cli só publica "latest" e versões completas.)
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

# Conta as linhas de TODAS as tabelas do schema public numa query só.
# query_to_xml permite rodar um count() dinâmico por tabela sem N chamadas.
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
# -Fc = custom format: comprimido e permite restauração seletiva (recuperar UMA
# tabela sem derrubar o resto). O Neon Free suspende o compute por inatividade e
# a primeira conexão o acorda -> connect_timeout generoso.
# --no-owner/--no-privileges: o dono no Neon (neondb_owner) não existe no
# container de teste, e num restore de emergência o dono é definido pelo destino.
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
# Comparar com a ORIGEM, e não checar "tem pelo menos uma linha", é o que pega
# dump truncado: 3 de 300 membros passaria numa checagem ingênua.
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
# Criptografia ASSIMÉTRICA: só a chave pública está aqui. Este script escreve
# backup e não consegue lê-lo. Se este ambiente for comprometido, o atacante
# leva arquivos que não abrem.
age -r "$AGE_PUBLIC_KEY" -o "${DUMP}.age" "$DUMP"

TAM_CIFRADO=$(stat -c%s "${DUMP}.age")
echo "    criptografado: ${TAM_CIFRADO} bytes"

# Sanidade: o age escreve um cabeçalho conhecido. Se não estiver lá, algo muito
# errado aconteceu e é melhor falhar do que subir lixo com nome de backup.
if ! head -c 21 "${DUMP}.age" | grep -q "age-encryption.org"; then
  echo "ERRO: arquivo criptografado sem o cabeçalho do age." >&2
  exit 1
fi

# R2 fala a API do S3; region=auto é o que ele espera.
# AWS CLI via Docker de propósito: o apt do Ubuntu traz a v1 e o runner do
# GitHub traz a v2 pré-instalada. Fixar a versão aqui faz local e CI rodarem
# exatamente o mesmo — um backup não pode "passar em dev e quebrar em prod".
docker run --rm \
  -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY -e AWS_DEFAULT_REGION=auto \
  -v "${WORKDIR}:/w:ro" \
  "$AWS_IMAGE" \
  s3 cp "/w/$(basename "${DUMP}.age")" "s3://${R2_BUCKET}/domus-${TS}.dump.age" \
  --endpoint-url "$R2_ENDPOINT" \
  --only-show-errors

echo "    enviado: s3://${R2_BUCKET}/domus-${TS}.dump.age"
echo "==> Backup concluído."
