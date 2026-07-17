# scripts

## backup-postgres.sh

Backup do Postgres do Neon: dump → teste de restauração → criptografia → upload pro R2.

Roda diariamente via `.github/workflows/backup-postgres.yml` (03:00 BRT). Este arquivo é a
lógica; o workflow só agenda e provê as dependências. Foi feito assim para poder ser rodado
**localmente, antes de confiar nele** — workflow só se testa empurrando commit.

### Rodar local

```bash
# ATENÇÃO: formato libpq, NÃO o jdbc: do backend/api/.env
export BACKUP_DATABASE_URL="postgresql://user:senha@host/neondb?sslmode=require"
./scripts/backup-postgres.sh --dry-run   # dump + teste, sem subir nada
```

O `--dry-run` precisa só de `docker`. O modo completo precisa também de `age` e das variáveis
`AGE_PUBLIC_KEY`, `R2_ENDPOINT`, `R2_BUCKET`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`.

`pg_dump`, `pg_restore`, `psql` e o AWS CLI rodam **via Docker**, com versão fixa
(`postgres:16`, `amazon/aws-cli:2`). Não instale por apt: o `awscli` do Ubuntu é a v1 e o
runner do GitHub traz a v2 — rodar versões diferentes local e no CI é como um backup passa em
dev e quebra em produção.

`postgres:16` porque o Neon roda **16.14**. Um `pg_dump` mais novo dumpa servidor mais antigo,
mas o contrário quebra — e usar a mesma versão da produção faz o teste ser fiel: num desastre
real é num Neon 16 que o dump vai entrar.

### Por que a ordem é dump → testa → criptografa → sobe

Só a chave **pública** do `age` existe no CI: ele criptografa e **não** descriptografa — é
exatamente isso que se quer (GitHub comprometido não lê backup). Logo, o teste de restauração
só pode acontecer **antes** da criptografia.

O teste compara a contagem de **cada tabela** contra a origem. Não é "tem pelo menos uma
linha": isso daria falso positivo num dump truncado (3 de 300 membros passaria) e falso
negativo num banco legitimamente vazio.

Verificado em 2026-07-17 truncando o dump de propósito: o `pg_restore` reprova e o script sai
com código 1. Um teste que nunca foi visto falhar não prova nada.

### O que este backup NÃO cobre

**A camada de criptografia.** Nenhuma automação prova que o arquivo abre com a sua chave —
o CI não tem a chave privada, de propósito. Só um humano fecha essa lacuna: ver o **ensaio
manual trimestral** no spec
(`backend/api/docs/superpowers/specs/2026-07-16-backup-postgres-design.md`).

⚠️ **A chave privada `age` é ponto único de falha.** Perdê-la torna todos os backups lixo.
Bitwarden + uma cópia offline.
