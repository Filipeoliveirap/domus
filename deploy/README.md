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

## Postgres

Não está aqui — é o Neon (Frankfurt), externo. Backup diário via GitHub Actions
(ver `scripts/backup-postgres.sh`).
