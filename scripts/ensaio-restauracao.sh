#!/usr/bin/env bash
#
# Ensaio de restauração do backup — o teste que a automação NÃO consegue fazer.
#
# O workflow diário prova que o dump é íntegro, mas NÃO prova que o arquivo
# criptografado abre com a sua chave: o CI só tem a chave pública, de propósito
# (assim um GitHub comprometido não lê backup nenhum). Essa lacuna só um humano
# fecha — e é este script.
#
# "Backup que nunca foi restaurado não é backup, é esperança."
#
# Rodar A CADA 3 MESES. Está no calendário; se não estiver, ponha.
#
# Uso:
#   ./scripts/ensaio-restauracao.sh              # usa o backup mais recente
#   ./scripts/ensaio-restauracao.sh <nome-do-objeto>
#
# Requer: docker, age. Nada é gravado fora de um diretório temporário, que é
# apagado com shred no fim (inclusive se der erro no meio).

set -Eeuo pipefail

R2_ENDPOINT="https://c37bf49031e81dc34228a4779adc53ad.r2.cloudflarestorage.com"
R2_BUCKET="domus-backups"
PG_IMAGE="postgres:16"
AWS_IMAGE="amazon/aws-cli:2.36.1"

WORKDIR="$(mktemp -d)"
CONTAINER="domus-ensaio-$$"
STTY_ORIG="$(stty -g 2>/dev/null || true)"

limpar() {
  # Restaura o eco do terminal SEMPRE. Se o script morrer com o eco desligado,
  # o terminal fica mudo e parece travado.
  [[ -n "$STTY_ORIG" ]] && stty "$STTY_ORIG" 2>/dev/null || stty echo 2>/dev/null || true
  docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
  # shred em tudo: a chave privada passa por aqui.
  find "$WORKDIR" -type f -exec shred -u {} + 2>/dev/null || true
  rm -rf "$WORKDIR"
  echo "    (temporários apagados com shred)"
}
trap limpar EXIT

aws_r2() {
  docker run --rm \
    -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY -e AWS_DEFAULT_REGION=auto \
    -v "$WORKDIR:/w" "$AWS_IMAGE" "$@" --endpoint-url "$R2_ENDPOINT"
}

echo "=== Ensaio de restauração do backup do Domus ==="
echo

if [[ -z "${AWS_ACCESS_KEY_ID:-}" || -z "${AWS_SECRET_ACCESS_KEY:-}" ]]; then
  # read -s: não ecoa e não vira comando, então não entra no histórico do shell.
  read -rsp "R2 Access Key ID: " AWS_ACCESS_KEY_ID; echo
  read -rsp "R2 Secret Access Key: " AWS_SECRET_ACCESS_KEY; echo
  export AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY
fi
echo

OBJETO="${1:-}"
if [[ -z "$OBJETO" ]]; then
  echo "==> 1/5 Procurando o backup mais recente"
  OBJETO=$(aws_r2 s3 ls "s3://${R2_BUCKET}/" | sort | tail -1 | awk '{print $4}')
  [[ -n "$OBJETO" ]] || { echo "ERRO: nenhum backup no bucket." >&2; exit 1; }
else
  echo "==> 1/5 Usando o objeto informado"
fi
echo "    $OBJETO"

echo "==> 2/5 Baixando do R2"
aws_r2 s3 cp "s3://${R2_BUCKET}/${OBJETO}" /w/backup.age --only-show-errors
echo "    $(stat -c%s "$WORKDIR/backup.age") bytes"

echo "==> 3/5 Descriptografando"
echo "    Cole as 3 linhas da chave PRIVADA (Bitwarden > Domus) e feche com Ctrl+D."
echo "    (o terminal NÃO vai exibir o que você colar — é esperado)"
# stty -echo em vez de um cat solto: o cat ECOA o que recebe, e a chave privada
# ficaria na tela e no scrollback do terminal. É a mesma proteção do read -s
# usado acima para as credenciais do R2.
stty -echo 2>/dev/null || true
cat > "$WORKDIR/chave.txt"
[[ -n "$STTY_ORIG" ]] && stty "$STTY_ORIG" 2>/dev/null || stty echo 2>/dev/null || true
chmod 600 "$WORKDIR/chave.txt"
echo "    (chave recebida: $(wc -l < "$WORKDIR/chave.txt") linhas)"

if ! age -d -i "$WORKDIR/chave.txt" -o "$WORKDIR/backup.dump" "$WORKDIR/backup.age" 2>"$WORKDIR/erro.txt"; then
  echo >&2
  echo "ERRO: a chave NÃO abre este backup." >&2
  sed 's/^/      /' "$WORKDIR/erro.txt" >&2
  echo >&2
  echo "  Isto é grave: significa que a chave guardada não é par da pública" >&2
  echo "  que está nos secrets, e TODOS os backups são ilegíveis." >&2
  echo "  Gere um par novo, atualize o secret AGE_PUBLIC_KEY e rode o backup" >&2
  echo "  de novo — antes que existam backups que importem." >&2
  exit 1
fi
echo "    OK: a chave abre o arquivo."

echo "==> 4/5 Restaurando num Postgres descartável"
docker run -d --name "$CONTAINER" \
  -e POSTGRES_PASSWORD=ensaio -e POSTGRES_DB=ensaio "$PG_IMAGE" >/dev/null
for _ in $(seq 1 30); do
  docker exec "$CONTAINER" pg_isready -U postgres -q 2>/dev/null && break
  sleep 1
done
docker cp "$WORKDIR/backup.dump" "$CONTAINER:/tmp/d.dump"
docker exec "$CONTAINER" pg_restore -U postgres -d ensaio --no-owner --no-privileges /tmp/d.dump

echo "==> 5/5 O que foi restaurado"
docker exec "$CONTAINER" psql -U postgres -d ensaio -tAc "
SELECT '    ' || rpad(tablename, 26) ||
       (xpath('/row/c/text()',
              query_to_xml(format('select count(*) as c from public.%I', tablename),
                           false, true, '')))[1]::text
FROM pg_tables WHERE schemaname='public' ORDER BY tablename;
"

echo
echo "=== Ensaio concluído. O backup é legível e restaurável. ==="
echo "    Confira acima se os números fazem sentido para a data do backup."
