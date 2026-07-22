# deploy — produção do Domus

Topologia (ver spec `backend/api/docs/superpowers/specs/2026-07-17-hospedagem-design.md`):

    Cloudflare → (Tunnel) → cloudflared → front (Next) → api (Spring) → Neon
                                                          redis · elasticsearch

## Subir / atualizar

    # na VPS, dentro de deploy/
    docker compose -f docker-compose.prod.yml pull      # puxa as imagens novas do ghcr.io
    docker compose -f docker-compose.prod.yml up -d      # sobe/recria só o que mudou

## Ver estado

    docker compose -f docker-compose.prod.yml ps
    docker compose -f docker-compose.prod.yml logs -f api

## Segredos

O `.env.prod` (com base no `.env.prod.example`) vive **só na VPS**, nunca no git.

## Fotos (Cloudflare R2) — antes do primeiro deploy

O upload de foto (pessoa, evento, logo da igreja) precisa de um bucket **privado**,
**separado do bucket de backup** (`domus-backups`, que é write-only por desenho). Antes
de subir:

1. Criar um bucket novo no Cloudflare R2, ex. `domus-fotos` — **privado** (sem acesso
   público; a imagem é sempre servida pela API, nunca por URL direta do R2).
2. Gerar um **R2 API Token** com permissão **Object Read & Write** (não bastar "leitura":
   o upload precisa escrever).
3. Preencher no `.env.prod`:
   - `R2_FOTOS_ENDPOINT`
   - `R2_FOTOS_BUCKET`
   - `R2_FOTOS_ACCESS_KEY`
   - `R2_FOTOS_SECRET_KEY`

⚠️ **O endpoint NÃO leva o nome do bucket.** O SDK já anexa o bucket na chamada; colocar
`https://<conta>.r2.cloudflarestorage.com/meu-bucket` faz o caminho virar
`/meu-bucket/meu-bucket/arquivo` e o upload falha. Use só
`https://<conta>.r2.cloudflarestorage.com`.

⚠️ **Sem essas variáveis (ou com token sem permissão de escrita), a aplicação sobe
normalmente** — o erro (`Access Denied` ou falha de conexão) só aparece no **primeiro
envio de foto**, não no boot. Testar um upload real depois do deploy é a única forma de
confirmar que a configuração está certa.

## Postgres

Não está aqui — é o Neon (Frankfurt), externo. Backup diário via GitHub Actions
(ver `scripts/backup-postgres.sh`).
