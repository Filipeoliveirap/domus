# Backup automático do Postgres — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Backup diário do Postgres do Neon, criptografado, guardado fora do Neon, com teste automático de restauração e alerta se parar de acontecer.

**Architecture:** Um script bash faz todo o trabalho (dump → teste de restauração → criptografia → upload) e um workflow do GitHub Actions só o agenda e provê as dependências. O script ser um arquivo separado é o que permite rodá-lo **localmente antes de confiar nele** — workflow só se testa empurrando commit.

**Tech Stack:** GitHub Actions (cron), `pg_dump`/`pg_restore` 16, Docker (`postgres:16` como alvo de restauração), `age` (criptografia assimétrica), AWS CLI (API S3 do Cloudflare R2), Sentry Crons (dead man's switch).

**Spec:** `backend/api/docs/superpowers/specs/2026-07-16-backup-postgres-design.md`

## Global Constraints

- Repositório único em `/home/jos-filipe-oliveira-pereira/Documents/domus`. Branch: `producao`.
- **Sem trailer `Co-Authored-By`** em commits.
- **Postgres do Neon: `16.14`.** Cliente `pg_dump`/`pg_restore` **16** (nunca inferior ao servidor).
- **Tabelas do schema `public` (9):** `categoria_financeira`, `evento`, `flyway_schema_history`, `igreja`, `membro`, `movimentacao_financeira`, `outbox`, `role`, `usuario`.
- **Chave pública `age`:** `age1ph0kgq900dst8snvvq5f9semm93879wpkufw9gtgjk9xx6ersqwszweehe`
- **R2:** bucket `domus-backups`, endpoint `https://c37bf49031e81dc34228a4779adc53ad.r2.cloudflarestorage.com`, região `auto`.
- **NUNCA** commitar segredo. **NUNCA** imprimir `BACKUP_DATABASE_URL`, chaves do R2 ou a chave privada `age` em log.
- `.github/workflows/` não existe ainda — este é o primeiro workflow do projeto.
- Repositório é **público** → minutos do Actions ilimitados; secrets **não** são expostos a PRs de forks.
- Ordem inegociável: **dump → testa em claro → criptografa → sobe.** Com só a chave pública o CI não consegue descriptografar.

---

### Task 1: Segredos e monitor do Sentry (configuração, sem código)

**Files:** nenhum (configuração em GitHub e Sentry)

**Interfaces:**
- Produces: os secrets `BACKUP_DATABASE_URL`, `R2_ENDPOINT`, `R2_BUCKET`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`, `AGE_PUBLIC_KEY`, `SENTRY_CRONS_URL` — consumidos pelas Tasks 4 e 5.

- [ ] **Step 1: Montar a `BACKUP_DATABASE_URL` no formato libpq**

⚠️ A `DATABASE_URL` do `.env` é **JDBC** e **não serve** para o `pg_dump`. Monte a string libpq a partir das três variáveis do `backend/api/.env`:

```
postgresql://<DATABASE_USERNAME>:<DATABASE_PASSWORD>@<host-e-path-da-DATABASE_URL-sem-o-prefixo-jdbc:postgresql://>
```

Exemplo do formato final (valores fictícios):
```
postgresql://neondb_owner:npg_xxxxx@ep-cold-xxxx.sa-east-1.aws.neon.tech/neondb?sslmode=require
```

Confira que funciona **antes** de virar secret:

```bash
docker run --rm -e PGURL="<a-url-libpq-montada>" postgres:16 \
  sh -c 'psql "$PGURL" -c "\dt"'
```
Expected: a listagem das 9 tabelas (`categoria_financeira`, `evento`, `flyway_schema_history`, `igreja`, `membro`, `movimentacao_financeira`, `outbox`, `role`, `usuario`).

Se der erro de autenticação ou de host, a URL está errada — **resolva antes de virar secret**, senão o erro só aparece na primeira execução do workflow.

- [ ] **Step 2: Criar o monitor no Sentry**

No Sentry (projeto do backend) → **Crons** → *Add Monitor*:

| Campo | Valor |
|---|---|
| Name | `Backup Postgres` |
| Slug | `backup-postgres` |
| Schedule type | Crontab |
| Cron | `0 6 * * *` |
| Timezone | `UTC` |
| Grace period (check-in margin) | `30` minutos |
| Max runtime | `20` minutos |
| Failure tolerance | `1` |

O cron `0 6 * * *` em UTC = **03:00 no horário de Brasília**.

Copie a **URL de check-in** que o Sentry exibe (formato
`https://o<org>.ingest.sentry.io/api/<project_id>/cron/backup-postgres/<key>/`). **Copie da tela do Sentry**, não monte à mão.

- [ ] **Step 3: Cadastrar os secrets no GitHub**

`https://github.com/Filipeoliveirap/domus/settings/secrets/actions` → *New repository secret*, sete vezes:

| Nome | Valor |
|---|---|
| `BACKUP_DATABASE_URL` | a string libpq do Step 1 |
| `R2_ENDPOINT` | `https://c37bf49031e81dc34228a4779adc53ad.r2.cloudflarestorage.com` |
| `R2_BUCKET` | `domus-backups` |
| `R2_ACCESS_KEY_ID` | Access Key ID do token do R2 |
| `R2_SECRET_ACCESS_KEY` | Secret Access Key do token do R2 |
| `AGE_PUBLIC_KEY` | `age1ph0kgq900dst8snvvq5f9semm93879wpkufw9gtgjk9xx6ersqwszweehe` |
| `SENTRY_CRONS_URL` | a URL do Step 2 |

- [ ] **Step 4: Conferir que os sete existem**

```bash
gh secret list --repo Filipeoliveirap/domus
```
Expected: os 7 nomes listados (o `gh` nunca mostra valores).

---

### Task 2: Script de backup — dump e teste de restauração

**Files:**
- Create: `scripts/backup-postgres.sh`
- Create: `scripts/README.md`

**Interfaces:**
- Consumes: env `BACKUP_DATABASE_URL` (Task 1).
- Produces: `scripts/backup-postgres.sh`, que lê as envs `BACKUP_DATABASE_URL`, `AGE_PUBLIC_KEY`, `R2_ENDPOINT`, `R2_BUCKET`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` e aceita a flag `--dry-run` (faz dump + teste, **não** criptografa nem sobe). Task 3 completa o mesmo arquivo; Task 4 o chama.

Esta task entrega dump + teste rodando **na sua máquina**. Nada de GitHub ainda.

- [ ] **Step 1: Criar o script**

Create `scripts/backup-postgres.sh`:

```bash
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
# Requer: docker, age, aws  (o --dry-run precisa só de docker)

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

# Versão do Postgres: DEVE ser >= a do servidor. Neon roda 16.14 (verificado
# em 2026-07-17). Um pg_dump mais novo dumpa servidor mais antigo; o contrário
# quebra. Usamos 16 (e não 17) para o teste restaurar na MESMA versão da
# produção — num desastre real é num Neon 16 que o dump vai entrar.
PG_IMAGE="postgres:16"

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

psql_origem() {
  docker run --rm -i -e PGURL="$BACKUP_DATABASE_URL" "$PG_IMAGE" \
    sh -c 'psql "$PGURL" -tAc "$(cat)"'
}

echo "==> 1/4 Dump do Neon (pg_dump -Fc)"
# -Fc = custom format: comprimido e permite restauração seletiva (recuperar UMA
# tabela sem derrubar o resto). Neon Free suspende o compute por inatividade, e
# a primeira conexão o acorda -> connect_timeout generoso.
docker run --rm -e PGURL="$BACKUP_DATABASE_URL" "$PG_IMAGE" \
  sh -c 'pg_dump "$PGURL?connect_timeout=30" -Fc --no-owner --no-privileges' > "$DUMP"

TAMANHO=$(stat -c%s "$DUMP")
echo "    dump: ${TAMANHO} bytes"
if [[ "$TAMANHO" -lt 1024 ]]; then
  echo "ERRO: dump menor que 1 KB — algo deu muito errado." >&2
  exit 1
fi

echo "==> 2/4 Contagens na origem"
echo "$SQL_CONTAGENS" | psql_origem | grep -v '^$' | sort > "$WORKDIR/origem.txt"
echo "    $(wc -l < "$WORKDIR/origem.txt") tabelas"
cat "$WORKDIR/origem.txt" | sed 's/^/      /'

echo "==> 3/4 Teste de restauração num Postgres descartável"
docker run -d --name "$CONTAINER" -e POSTGRES_PASSWORD=teste -e POSTGRES_DB=restore_test "$PG_IMAGE" >/dev/null
for _ in $(seq 1 30); do
  docker exec "$CONTAINER" pg_isready -U postgres -q && break
  sleep 1
done
docker exec "$CONTAINER" pg_isready -U postgres -q || { echo "ERRO: Postgres de teste não subiu." >&2; exit 1; }

docker cp "$DUMP" "$CONTAINER:/tmp/d.dump"
# --no-owner/--no-privileges: o dono no Neon (neondb_owner) não existe aqui.
docker exec "$CONTAINER" pg_restore -U postgres -d restore_test --no-owner --no-privileges /tmp/d.dump

docker exec -i "$CONTAINER" psql -U postgres -d restore_test -tAc "$SQL_CONTAGENS" \
  | grep -v '^$' | sort > "$WORKDIR/restaurado.txt"

echo "    comparando contagens origem x restaurado..."
if ! diff -u "$WORKDIR/origem.txt" "$WORKDIR/restaurado.txt"; then
  echo "ERRO: as contagens divergem. O dump está incompleto ou corrompido." >&2
  echo "      (a esquerda = Neon, a direita = restaurado)" >&2
  exit 1
fi
echo "    OK: todas as tabelas bateram."

if [[ "$DRY_RUN" == true ]]; then
  echo "==> dry-run: parando aqui. Nada foi criptografado nem enviado."
  exit 0
fi

echo "==> 4/4 (implementado na Task 3)"
```

- [ ] **Step 2: Tornar executável e rodar o dry-run**

```bash
cd /home/jos-filipe-oliveira-pereira/Documents/domus
chmod +x scripts/backup-postgres.sh
cd backend/api
set -a && . <(sed 's/^\(EMAIL_FROM\)=\(.*\)$/\1="\2"/' ./.env) && set +a
HOSTPATH=$(echo "$DATABASE_URL" | sed -E 's#^jdbc:postgresql://##')
export BACKUP_DATABASE_URL="postgresql://${DATABASE_USERNAME}:${DATABASE_PASSWORD}@${HOSTPATH}"
cd /home/jos-filipe-oliveira-pereira/Documents/domus
./scripts/backup-postgres.sh --dry-run
```
Expected: as 4 etapas, as 9 tabelas listadas com suas contagens, `OK: todas as tabelas bateram.` e `dry-run: parando aqui`.

- [ ] **Step 3: Provocar a falha — o teste tem que reprovar um dump ruim**

Um teste que nunca foi visto falhar não prova nada. Truncar o dump de propósito e confirmar que o script reprova:

Corte o dump para **2000 bytes**: passa da checagem de 1 KB (então não é ela que pega) e chega quebrado no `pg_restore` — que é o caminho que queremos exercitar.

```bash
cd /home/jos-filipe-oliveira-pereira/Documents/domus
sed -i 's|^TAMANHO=\$(stat -c%s "\$DUMP")|truncate -s 2000 "$DUMP"   # SABOTAGEM TEMPORARIA\nTAMANHO=$(stat -c%s "$DUMP")|' scripts/backup-postgres.sh
grep -n "SABOTAGEM" scripts/backup-postgres.sh   # confirme que a linha entrou
./scripts/backup-postgres.sh --dry-run; echo "EXIT=$?"
```
Expected: o `pg_restore` reclama do arquivo e o script **para**. **`EXIT` diferente de 0.** É esse o comportamento que queremos ver com os próprios olhos.

Desfazer a sabotagem e confirmar que voltou a passar:

```bash
sed -i '/SABOTAGEM TEMPORARIA/d' scripts/backup-postgres.sh
grep -c "SABOTAGEM" scripts/backup-postgres.sh   # tem que dar 0
./scripts/backup-postgres.sh --dry-run; echo "EXIT=$?"
```
Expected: `0` sabotagens e `EXIT=0`.

- [ ] **Step 4: Documentar**

Create `scripts/README.md`:

```markdown
# scripts

## backup-postgres.sh

Backup do Postgres do Neon: dump → teste de restauração → criptografia → upload pro R2.

Roda diariamente via `.github/workflows/backup-postgres.yml` (03:00 BRT). Este arquivo é a
lógica; o workflow só agenda e provê as dependências. Foi feito assim para poder ser rodado
**localmente**, antes de confiar nele — workflow só se testa empurrando commit.

### Rodar local

```bash
export BACKUP_DATABASE_URL="postgresql://user:senha@host/neondb?sslmode=require"  # libpq, NÃO jdbc
./scripts/backup-postgres.sh --dry-run   # dump + teste, sem subir nada
```

O `--dry-run` precisa só de `docker`. O modo completo precisa também de `age`.

`pg_dump`, `pg_restore`, `psql` e o AWS CLI são usados **via Docker**, com versão fixa
(`postgres:16`, `amazon/aws-cli:2`). Não instale por apt: o `awscli` do Ubuntu é a v1 e o
runner do GitHub traz a v2 — rodar versões diferentes local e no CI é como um backup passa em
dev e quebra em produção.

### Por que a ordem é dump → testa → criptografa → sobe

Só a chave **pública** do `age` existe no CI: ele criptografa e **não** descriptografa — é
exatamente isso que se quer (GitHub comprometido não lê backup). Logo, o teste de restauração
só pode acontecer **antes** da criptografia.

O teste compara a contagem de **cada tabela** contra a origem. Não é "tem pelo menos uma
linha": isso daria falso positivo num dump truncado (3 de 300 membros passaria) e falso
negativo num banco legitimamente vazio.

### O que este backup NÃO cobre

A camada de criptografia. Nenhuma automação prova que o arquivo abre com a sua chave — só um
humano. Ver o **ensaio manual trimestral** no spec
(`backend/api/docs/superpowers/specs/2026-07-16-backup-postgres-design.md`).
```

- [ ] **Step 5: Commit**

```bash
cd /home/jos-filipe-oliveira-pereira/Documents/domus
git add scripts/backup-postgres.sh scripts/README.md
git commit -m "feat(backup): script de dump do Postgres com teste de restauração

O dump é restaurado num Postgres descartável e as contagens de cada
tabela são comparadas contra a origem. Comparar com a origem, e não
checar 'tem linha', é o que pega dump truncado — 3 de 300 membros
passaria numa checagem ingênua.

Fica num script, e não no workflow, para poder ser rodado localmente:
workflow só se testa empurrando commit.

Postgres 16 (Neon roda 16.14) para o teste restaurar na mesma versão
que a produção."
```

---

### Task 3: Script — criptografia e upload para o R2

**Files:**
- Modify: `scripts/backup-postgres.sh` (a etapa `4/4`)

**Interfaces:**
- Consumes: `AGE_PUBLIC_KEY`, `R2_ENDPOINT`, `R2_BUCKET`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`.
- Produces: objeto `domus-<TS>.dump.age` no bucket.

- [ ] **Step 1: Conferir o `age`**

```bash
age --version
```
Expected: `1.2.1` (ou superior).

**O AWS CLI NÃO deve ser instalado.** O script vai usá-lo via Docker (`amazon/aws-cli:2`), igual ao Postgres. Motivo: o `awscli` do apt do Ubuntu é a **v1**, enquanto o runner do GitHub já traz a **v2** pré-instalada — instalar por apt faria a máquina local rodar v1 e o CI rodar v2, contra o mesmo R2 (que já teve incompatibilidade de checksum com a v2). Testar uma coisa e entregar outra é exatamente o que a gente não quer num backup.

- [ ] **Step 2: Substituir a última linha do script**

Em `scripts/backup-postgres.sh`, troque a linha
`echo "==> 4/4 (implementado na Task 3)"` por:

```bash
echo "==> 4/4 Criptografando e enviando"
# Criptografia ASSIMÉTRICA: só a chave pública está aqui. Este script escreve
# backup e não consegue lê-lo. Se este ambiente for comprometido, o atacante
# leva arquivos que não abrem.
age -r "$AGE_PUBLIC_KEY" -o "${DUMP}.age" "$DUMP"

TAM_CIFRADO=$(stat -c%s "${DUMP}.age")
echo "    criptografado: ${TAM_CIFRADO} bytes"

# Sanidade: o age escreve um cabeçalho conhecido. Se não estiver lá, algo
# muito errado aconteceu e é melhor falhar do que subir lixo.
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
  amazon/aws-cli:2 \
  s3 cp "/w/$(basename "${DUMP}.age")" "s3://${R2_BUCKET}/domus-${TS}.dump.age" \
  --endpoint-url "$R2_ENDPOINT" \
  --only-show-errors

echo "    enviado: s3://${R2_BUCKET}/domus-${TS}.dump.age"
echo "==> Backup concluído."
```

- [ ] **Step 3: Rodar completo, de verdade**

```bash
cd /home/jos-filipe-oliveira-pereira/Documents/domus/backend/api
set -a && . <(sed 's/^\(EMAIL_FROM\)=\(.*\)$/\1="\2"/' ./.env) && set +a
HOSTPATH=$(echo "$DATABASE_URL" | sed -E 's#^jdbc:postgresql://##')
export BACKUP_DATABASE_URL="postgresql://${DATABASE_USERNAME}:${DATABASE_PASSWORD}@${HOSTPATH}"
export AGE_PUBLIC_KEY="age1ph0kgq900dst8snvvq5f9semm93879wpkufw9gtgjk9xx6ersqwszweehe"
export R2_ENDPOINT="https://c37bf49031e81dc34228a4779adc53ad.r2.cloudflarestorage.com"
export R2_BUCKET="domus-backups"
read -rsp "R2 Access Key ID: " AWS_ACCESS_KEY_ID; echo; export AWS_ACCESS_KEY_ID
read -rsp "R2 Secret Access Key: " AWS_SECRET_ACCESS_KEY; echo; export AWS_SECRET_ACCESS_KEY
cd /home/jos-filipe-oliveira-pereira/Documents/domus
./scripts/backup-postgres.sh
```
Expected: as 4 etapas e `Backup concluído.`

(O `read -rsp` evita as chaves ficarem no histórico do shell.)

- [ ] **Step 4: Confirmar no R2**

```bash
docker run --rm -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY -e AWS_DEFAULT_REGION=auto \
  amazon/aws-cli:2 s3 ls "s3://domus-backups/" \
  --endpoint-url "https://c37bf49031e81dc34228a4779adc53ad.r2.cloudflarestorage.com"
```
Expected: um objeto `domus-<timestamp>.dump.age`.

- [ ] **Step 5: A prova real — o arquivo abre com SUA chave?**

⚠️ **Rode no seu terminal. A chave privada não entra nesta conversa.**

Baixe o objeto, descriptografe com a chave privada do Bitwarden e confira que é um dump de verdade:

```bash
# pega o nome do objeto mais recente, sem digitar timestamp à mão
OBJ=$(docker run --rm -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY -e AWS_DEFAULT_REGION=auto \
  amazon/aws-cli:2 s3 ls "s3://domus-backups/" \
  --endpoint-url "https://c37bf49031e81dc34228a4779adc53ad.r2.cloudflarestorage.com" \
  | sort | tail -1 | awk '{print $4}')
echo "baixando: $OBJ"

mkdir -p /tmp/ensaio && cd /tmp/ensaio
docker run --rm -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY -e AWS_DEFAULT_REGION=auto \
  -v /tmp/ensaio:/w amazon/aws-cli:2 s3 cp "s3://domus-backups/$OBJ" "/w/t.age" \
  --endpoint-url "https://c37bf49031e81dc34228a4779adc53ad.r2.cloudflarestorage.com"

# cole a chave privada (as 3 linhas do Bitwarden) e feche com Ctrl+D
cat > /tmp/ensaio/k.txt

age -d -i /tmp/ensaio/k.txt -o /tmp/ensaio/t.dump /tmp/ensaio/t.age
docker run --rm -v /tmp/ensaio/t.dump:/d.dump:ro postgres:16 pg_restore --list /d.dump | head -20

shred -u /tmp/ensaio/k.txt /tmp/ensaio/t.dump /tmp/ensaio/t.age
```
Expected: o `pg_restore --list` mostra o índice do dump (as tabelas). **É este passo que prova que a chave guardada é a certa** — a automação nunca vai conseguir provar isso, porque o CI não tem (nem pode ter) a chave privada.

Se o `age -d` falhar aqui, **pare tudo**: a chave guardada não corresponde à pública que está nos secrets, e todo backup gerado até agora é ilegível. Nesse caso, gere um par novo e refaça — enquanto o prejuízo ainda é zero.

- [ ] **Step 6: Commit**

```bash
cd /home/jos-filipe-oliveira-pereira/Documents/domus
git add scripts/backup-postgres.sh
git commit -m "feat(backup): criptografia age e upload pro Cloudflare R2

Criptografia assimétrica: só a chave pública vive no ambiente que gera o
backup, então ele escreve e não lê. Ambiente comprometido leva arquivos
que não abrem.

Confere o cabeçalho do age antes de subir: melhor falhar do que enviar
lixo com nome de backup."
```

---

### Task 4: Workflow do GitHub Actions

**Files:**
- Create: `.github/workflows/backup-postgres.yml`

**Interfaces:**
- Consumes: os 7 secrets (Task 1) e `scripts/backup-postgres.sh` (Tasks 2 e 3).
- Produces: execução diária às 03:00 BRT + `workflow_dispatch` manual.

- [ ] **Step 1: Criar o workflow**

Create `.github/workflows/backup-postgres.yml`:

```yaml
name: Backup do Postgres

on:
  schedule:
    # 06:00 UTC = 03:00 BRT. O sistema está parado a essa hora.
    - cron: '0 6 * * *'
  workflow_dispatch:

# Nunca dois backups ao mesmo tempo.
concurrency:
  group: backup-postgres
  cancel-in-progress: false

jobs:
  backup:
    runs-on: ubuntu-latest
    timeout-minutes: 20

    steps:
      - uses: actions/checkout@v4

      # Dead man's switch: se este job não rodar (workflow desativado por 60
      # dias sem commit, GitHub fora do ar, o que for), o check-in não chega e
      # o Sentry avisa. Em vez de prever cada forma de quebrar, monitora-se a
      # única coisa que importa: "o backup aconteceu?".
      - name: Sentry — avisar que começou
        run: curl -sS -m 10 -X POST "${{ secrets.SENTRY_CRONS_URL }}?status=in_progress" || true

      # Só o age. O AWS CLI e o Postgres o script usa via Docker (versão fixa),
      # justamente para o CI rodar o mesmo que a máquina local.
      - name: Instalar age
        run: |
          sudo apt-get update -qq
          sudo apt-get install -y -qq age

      - name: Rodar o backup
        env:
          BACKUP_DATABASE_URL: ${{ secrets.BACKUP_DATABASE_URL }}
          AGE_PUBLIC_KEY: ${{ secrets.AGE_PUBLIC_KEY }}
          R2_ENDPOINT: ${{ secrets.R2_ENDPOINT }}
          R2_BUCKET: ${{ secrets.R2_BUCKET }}
          AWS_ACCESS_KEY_ID: ${{ secrets.R2_ACCESS_KEY_ID }}
          AWS_SECRET_ACCESS_KEY: ${{ secrets.R2_SECRET_ACCESS_KEY }}
        run: ./scripts/backup-postgres.sh

      - name: Sentry — sucesso
        if: success()
        run: curl -sS -m 10 -X POST "${{ secrets.SENTRY_CRONS_URL }}?status=ok" || true

      - name: Sentry — falha
        if: failure()
        run: curl -sS -m 10 -X POST "${{ secrets.SENTRY_CRONS_URL }}?status=error" || true
```

Nota sobre os `|| true` nos check-ins: se o Sentry estiver fora do ar, isso **não pode** derrubar o backup. O backup é o fim; o monitoramento é o meio.

- [ ] **Step 2: Validar a sintaxe antes de empurrar**

```bash
cd /home/jos-filipe-oliveira-pereira/Documents/domus
python3 -c "import yaml,sys; yaml.safe_load(open('.github/workflows/backup-postgres.yml')); print('YAML OK')"
```
Expected: `YAML OK`

- [ ] **Step 3: Commit e push**

```bash
cd /home/jos-filipe-oliveira-pereira/Documents/domus
git add .github/workflows/backup-postgres.yml
git commit -m "feat(backup): workflow diário no GitHub Actions

Roda 03:00 BRT e faz check-in no Sentry Crons. O check-in é o dead man's
switch: o GitHub desativa workflow agendado após 60 dias sem commit, e
sem monitor o backup pararia em silêncio. Ausência de check-in vira
alerta, qualquer que seja a causa.

Os check-ins usam || true de propósito: Sentry fora do ar não pode
derrubar o backup."
git push
```

- [ ] **Step 4: Disparar manualmente e acompanhar**

```bash
gh workflow run "Backup do Postgres" --repo Filipeoliveirap/domus
sleep 20
gh run list --workflow="Backup do Postgres" --repo Filipeoliveirap/domus --limit 1
```
Depois:
```bash
gh run watch --repo Filipeoliveirap/domus
```
Expected: conclusão com sucesso. Se falhar, `gh run view --log-failed` mostra o passo.

- [ ] **Step 5: Confirmar o objeto novo no R2 e o monitor no Sentry**

```bash
docker run --rm -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY -e AWS_DEFAULT_REGION=auto \
  amazon/aws-cli:2 s3 ls "s3://domus-backups/" \
  --endpoint-url "https://c37bf49031e81dc34228a4779adc53ad.r2.cloudflarestorage.com"
```
Expected: um objeto novo, com timestamp de agora.

No Sentry → Crons → `Backup Postgres`: status **ok**, com o check-in recém-chegado.

---

### Task 5: Validar os alertas e fechar o item

**Files:**
- Modify: `backend/api/CLAUDE.md` (marcar o item do roadmap)
- Modify: `backend/api/docs/BACKLOG-DIVIDA-E-PROXIMO-SCOPE.md`

**Interfaces:**
- Consumes: tudo das Tasks 1-4.

Um alerta que nunca disparou é tão confiável quanto um backup nunca restaurado.

- [ ] **Step 1: Provar que a falha do job vira alerta**

Dispare o workflow com um segredo quebrado, temporariamente:

```bash
gh secret set BACKUP_DATABASE_URL --repo Filipeoliveirap/domus --body "postgresql://errado:errado@localhost:5432/naoexiste"
gh workflow run "Backup do Postgres" --repo Filipeoliveirap/domus
sleep 30 && gh run watch --repo Filipeoliveirap/domus || true
```
Expected: o job **falha**, o check-in `status=error` é enviado, e o Sentry cria um issue + e-mail.

**Restaure o secret correto imediatamente** (Task 1, Step 1) e rode de novo até passar:

```bash
gh secret set BACKUP_DATABASE_URL --repo Filipeoliveirap/domus   # cola a url libpq correta, Ctrl+D
gh workflow run "Backup do Postgres" --repo Filipeoliveirap/domus
```

- [ ] **Step 2: Confirmar a regra de retenção no R2**

No painel do R2 → `domus-backups` → Settings → Object lifecycle rules: a regra `retencao-90-dias` está **enabled**, com *delete after 90 days* e *abort incomplete multipart uploads after 1 day*.

- [ ] **Step 3: Agendar o ensaio manual trimestral**

Crie um evento **recorrente a cada 3 meses** no seu calendário: **"Domus — ensaio de restauração do backup"**, com esta descrição:

```
1. Baixar o backup mais recente do R2 (bucket domus-backups)
2. Descriptografar com a chave privada age (Bitwarden > Domus)
3. Restaurar num Postgres 16 local e conferir os dados
Passo a passo: backend/api/docs/superpowers/specs/2026-07-16-backup-postgres-design.md
```

Isto não é burocracia: a automação **não consegue** provar que o arquivo criptografado abre
com a sua chave (o CI não tem a chave privada — de propósito). Só um humano fecha essa
lacuna.

- [ ] **Step 4: Marcar no roadmap**

Em `backend/api/CLAUDE.md`, substituir o item de backup por:

```markdown
- [x] **Backup automático do Postgres** — **FEITO e validado** (2026-07-17): workflow diário
  no GitHub Actions (03:00 BRT) → `pg_dump -Fc` → **teste de restauração** num Postgres 16
  descartável comparando a contagem de cada tabela contra a origem → criptografia **`age`
  assimétrica** (só a chave pública no CI: ele escreve e não lê) → **Cloudflare R2**
  (`domus-backups`, retenção de 90 dias por lifecycle rule).
  **Monitorado por Sentry Crons** (dead man's switch): o GitHub desativa workflow agendado
  após 60 dias sem commit, então em vez de prever cada falha, monitora-se "o backup
  aconteceu?". Validado provocando as falhas, não só vendo passar.
  **Motivação:** o Neon Free dá só **6h** de PITR — o dump externo não é a segunda rede de
  segurança, é a única. E backup no mesmo provedor que o dado é redundância, não backup.
  ⚠️ **A chave privada `age` é ponto único de falha** (Bitwarden + cópia offline).
  **Ensaio manual trimestral** no calendário — a automação não prova que o arquivo abre.
  Ver spec/plano em `docs/superpowers/`.
```

- [ ] **Step 5: Registrar o que ficou de fora**

Em `backend/api/docs/BACKLOG-DIVIDA-E-PROXIMO-SCOPE.md`, na seção
`## Dívida técnica (adiada de propósito, YAGNI/tempo)`:

```markdown
- **Backup: janela de perda de 24h e restauração manual.** O backup roda 1×/dia, então o pior
  caso é perder um dia de lançamentos. Aceito: a igreja lança dízimo no domingo e cadastra
  membro na quarta; redigitar isso é barato perto de dobrar as peças. Também não há
  automação de *restore* — restaurar é manual e proposital (restauração automática é como se
  apaga produção por engano). Se o volume crescer, avaliar 2×/dia.

- **Backup depende do GitHub Actions estar habilitado.** Workflows agendados são desativados
  após 60 dias sem commit no repositório. Mitigado pelo Sentry Crons (avisa em ~24h), não
  eliminado. Se o projeto entrar em hibernação longa, reativar na mão.
```

- [ ] **Step 6: Commit e push**

```bash
cd /home/jos-filipe-oliveira-pereira/Documents/domus
git add backend/api/docs/BACKLOG-DIVIDA-E-PROXIMO-SCOPE.md
git commit -m "docs(backlog): resíduos do backup do Postgres

Janela de 24h de perda, ausência de restauração automatizada (de
propósito) e a dependência do Actions seguir habilitado."
git push
```

---

## Verificação final

- [ ] `./scripts/backup-postgres.sh --dry-run` passa localmente
- [ ] O teste **reprova** um dump truncado (visto falhar, não só passar)
- [ ] O workflow roda pelo `workflow_dispatch` e o objeto aparece no R2
- [ ] O objeto **abre com a chave privada** e o `pg_restore --list` mostra as tabelas
- [ ] O Sentry mostra o monitor `ok` — e mostrou `error` quando provocado
- [ ] Lifecycle rule de 90 dias ativa
- [ ] Ensaio trimestral no calendário
- [ ] `git status` limpo, nenhum segredo commitado
