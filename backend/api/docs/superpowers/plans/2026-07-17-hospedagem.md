# Hospedagem de produção — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Colocar o Domus no ar em `domusigreja.com.br` — VPS na Europa rodando tudo em docker-compose, atrás do Cloudflare Tunnel, com o banco no Neon (Frankfurt) e o primeiro deploy feito à mão.

**Architecture:** Imagens Docker são buildadas no GitHub Actions e publicadas no `ghcr.io`; a VPS só puxa e sobe. `cloudflared` faz o transporte seguro (nenhuma porta aberta); o Next é a porta de entrada e roteia `/api` pro Spring; Redis e ES ficam na rede interna; o Postgres é o Neon externo.

**Tech Stack:** Docker + docker-compose, GitHub Actions + GitHub Container Registry, Cloudflare Tunnel, Hetzner Cloud, Neon (Postgres gerenciado), Java 21/Spring Boot, Next.js 16.

**Spec:** `backend/api/docs/superpowers/specs/2026-07-17-hospedagem-design.md`

## Global Constraints

- Repositório único em `/home/jos-filipe-oliveira-pereira/Documents/domus`. Trabalhar na branch `producao`, PR pra `main` com **merge commit** (não squash — ver a lição do backup).
- **Sem trailer `Co-Authored-By`** em commits.
- **Nenhuma porta aberta na VPS** — acesso externo só via Cloudflare Tunnel. SSH só por chave.
- **Nenhum segredo no git.** `.env` de produção vive na VPS; secrets de build vivem no GitHub.
- **Deploy manual primeiro.** Automação (CI/CD) é item seguinte do roadmap, fora deste plano.
- **Testes de back: Mockito puro**, sem contexto Spring.
- Serviços no compose de prod: `cloudflared`, `front` (Next), `api` (Spring), `redis`, `elasticsearch`. **Postgres NÃO** — é o Neon.
- Região: VPS Hetzner (Alemanha) + Neon em Frankfurt. `RATELIMIT_TRUST_FORWARDED_FOR=true` e `FORWARD_HEADERS_STRATEGY=framework` em prod (há proxy confiável na frente).

---

### Task 1: RateLimitFilter lê o `CF-Connecting-IP`

**Files:**
- Modify: `backend/api/src/main/java/com/domus/api/shared/security/RateLimitFilter.java` (método `resolverIp`)
- Test: `backend/api/src/test/java/com/domus/api/shared/security/RateLimitFilterTest.java`

**Interfaces:**
- Consumes: nada novo (mesmo construtor).
- Produces: com `trust-forwarded-for=true`, o IP do cliente vem do header `CF-Connecting-IP` (que a Cloudflare sempre injeta), com fallback pro `X-Forwarded-For` e depois pro socket.

Por quê: atrás da Cloudflare + túnel, o `X-Forwarded-For` fica ambíguo (pode ter itens forjados pelo cliente antes da Cloudflare). O `CF-Connecting-IP` é o IP real, sempre, sem ambiguidade. Fecha a dívida anotada no BACKLOG.

- [ ] **Step 1: Write the failing test**

Adicionar em `RateLimitFilterTest.java`:

```java
    @Test
    void comTrust_usaCfConnectingIp() throws Exception {
        when(request.getRequestURI()).thenReturn("/membros");
        when(request.getHeader("CF-Connecting-IP")).thenReturn("9.9.9.9");
        when(request.getHeader("X-Forwarded-For")).thenReturn("6.6.6.6, 1.1.1.1");
        incrementaPara("rl:global:", 1L);

        filtro(100, 10, true).doFilter(request, response, chain);

        // A chave do contador tem que ser keyada no IP da Cloudflare, não no XFF nem no socket.
        verify(valueOps).increment(startsWith("rl:global:9.9.9.9:"));
        verify(chain).doFilter(request, response);
    }

    @Test
    void comTrust_semCfHeader_caiNoXForwardedFor() throws Exception {
        when(request.getRequestURI()).thenReturn("/membros");
        when(request.getHeader("CF-Connecting-IP")).thenReturn(null);
        when(request.getHeader("X-Forwarded-For")).thenReturn("6.6.6.6, 1.1.1.1");
        incrementaPara("rl:global:", 1L);

        filtro(100, 10, true).doFilter(request, response, chain);

        verify(valueOps).increment(startsWith("rl:global:6.6.6.6:"));
    }

    @Test
    void semTrust_ignoraOsHeadersEUsaOSocket() throws Exception {
        when(request.getRequestURI()).thenReturn("/membros");
        // headers presentes, mas sem trust não podem ser confiados (seriam forjáveis)
        lenient().when(request.getHeader("CF-Connecting-IP")).thenReturn("9.9.9.9");
        incrementaPara("rl:global:", 1L);

        filtro(100, 10, false).doFilter(request, response, chain);

        verify(valueOps).increment(startsWith("rl:global:1.2.3.4:")); // getRemoteAddr do setup
    }
```

- [ ] **Step 2: Run — deve falhar**

```bash
cd backend/api && mvn -o test -Dtest=RateLimitFilterTest 2>&1 | grep -E "Tests run|comTrust_usaCfConnectingIp"
```
Expected: FAIL em `comTrust_usaCfConnectingIp` (hoje o filtro lê o XFF, então a chave sai `rl:global:6.6.6.6`, não `9.9.9.9`).

- [ ] **Step 3: Implement**

Em `RateLimitFilter.java`, substituir o método `resolverIp`:

```java
    /**
     * IP de origem. Por padrão usa o IP do socket. Só confia em headers quando há um proxy
     * confiável na frente (trust-forwarded-for=true) — confiar sem proxy permitiria forjar o
     * IP e escapar/poluir o limite.
     *
     * <p>Atrás da Cloudflare, prefere {@code CF-Connecting-IP}: é o IP real do cliente, sempre,
     * sem a ambiguidade do {@code X-Forwarded-For} (que pode conter itens forjados pelo cliente
     * antes de chegar na Cloudflare). Cai no XFF só se o CF não vier.
     */
    private String resolverIp(HttpServletRequest request) {
        if (trustForwardedFor) {
            String cf = request.getHeader("CF-Connecting-IP");
            if (cf != null && !cf.isBlank()) {
                return cf.trim();
            }
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
```

- [ ] **Step 4: Run — deve passar**

```bash
cd backend/api && set -a && . <(sed 's/^\(EMAIL_FROM\)=\(.*\)$/\1="\2"/' ./.env) && set +a && mvn -o test 2>&1 | grep -E "Tests run: [0-9]+, Fail|BUILD"
```
Expected: PASS — a suíte inteira (43 + 3 novos = 46), 0 falhas.

- [ ] **Step 5: Commit**

```bash
cd /home/jos-filipe-oliveira-pereira/Documents/domus
git add backend/api/src/main/java/com/domus/api/shared/security/RateLimitFilter.java \
        backend/api/src/test/java/com/domus/api/shared/security/RateLimitFilterTest.java
git commit -m "feat(security): rate limiting lê CF-Connecting-IP atrás da Cloudflare

Com a Cloudflare na frente, o X-Forwarded-For fica ambíguo (pode ter
itens forjados pelo cliente antes da Cloudflare). O CF-Connecting-IP é o
IP real, sempre. Prefere ele quando trust está ligado; cai no XFF e
depois no socket. Fecha a dívida anotada no BACKLOG."
```

---

### Task 2: Dockerfile de produção do Next (output standalone)

**Files:**
- Modify: `frontend/next.config.ts` (adicionar `output: 'standalone'`)
- Create: `frontend/Dockerfile`
- Create: `frontend/.dockerignore`

**Interfaces:**
- Produces: imagem do front que sobe com `node server.js` na porta 3000, servindo as telas e roteando `/api` pro `http://api:8080` (via `API_INTERNAL_URL` em runtime). `NEXT_PUBLIC_API_URL=/api` é **assado no build**.

`output: 'standalone'` faz o Next empacotar só o necessário (sem `node_modules` inteiro) — imagem de ~150 MB em vez de ~1 GB.

- [ ] **Step 1: Ligar o standalone**

Em `frontend/next.config.ts`, dentro do `nextConfig`, adicionar como primeira propriedade:

```ts
const nextConfig: NextConfig = {
  // Empacota um servidor mínimo (sem node_modules inteiro) — imagem Docker enxuta.
  output: "standalone",
  async rewrites() {
```

- [ ] **Step 2: Criar o `.dockerignore`**

Create `frontend/.dockerignore`:

```
node_modules
.next
.git
.env*
npm-debug.log
Dockerfile
.dockerignore
```

- [ ] **Step 3: Criar o Dockerfile**

Create `frontend/Dockerfile`:

```dockerfile
# ---- build ----
FROM node:22-alpine AS build
WORKDIR /app

# NEXT_PUBLIC_* são ASSADOS no build. /api é o caminho same-origin (não é segredo).
ARG NEXT_PUBLIC_API_URL=/api
ENV NEXT_PUBLIC_API_URL=$NEXT_PUBLIC_API_URL

COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# ---- runtime ----
FROM node:22-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production
# API_INTERNAL_URL é lido em runtime pelo rewrites() (server-side). Aponta pro container do Spring.
ENV API_INTERNAL_URL=http://api:8080

# O standalone traz o server.js + só as deps usadas.
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
COPY --from=build /app/public ./public

EXPOSE 3000
CMD ["node", "server.js"]
```

- [ ] **Step 4: Buildar e subir local pra provar que a imagem funciona**

```bash
cd frontend
docker build -t domus-front:teste .
docker run --rm -d --name domus-front-teste -p 3300:3000 -e API_INTERNAL_URL=http://localhost:8080 domus-front:teste
sleep 4
curl -s -o /dev/null -w "  HTTP %{http_code}\n" http://localhost:3300/login
docker rm -f domus-front-teste
```
Expected: `HTTP 200` (a tela de login renderiza). Se der erro de standalone, confira o `output` no next.config.

- [ ] **Step 5: Commit**

```bash
cd /home/jos-filipe-oliveira-pereira/Documents/domus
git add frontend/next.config.ts frontend/Dockerfile frontend/.dockerignore
git commit -m "build(front): Dockerfile de produção com output standalone

Imagem enxuta (~150MB): o Next empacota só o server.js e as deps usadas.
NEXT_PUBLIC_API_URL=/api é assado no build; API_INTERNAL_URL (destino do
rewrite pro Spring) é runtime, aponta pro container api."
```

---

### Task 3: docker-compose de produção + template de env

**Files:**
- Create: `deploy/docker-compose.prod.yml`
- Create: `deploy/.env.prod.example`
- Create: `deploy/README.md`

**Interfaces:**
- Consumes: imagens `ghcr.io/filipeoliveirap/domus-api` e `domus-front` (Task 4); variáveis do `.env.prod`.
- Produces: a topologia de prod rodável na VPS com `docker compose -f docker-compose.prod.yml up -d`.

Fica numa pasta `deploy/` na raiz — separa a config de produção do código.

- [ ] **Step 1: Criar o compose de produção**

Create `deploy/docker-compose.prod.yml`:

```yaml
name: domus

services:
  api:
    image: ghcr.io/filipeoliveirap/domus-api:latest
    restart: unless-stopped
    env_file: .env.prod
    environment:
      SPRING_PROFILES_ACTIVE: prod
      SPRING_DATA_REDIS_HOST: redis
      SPRING_ELASTICSEARCH_URIS: http://elasticsearch:9200
    depends_on:
      redis:
        condition: service_healthy
      elasticsearch:
        condition: service_healthy
    # SEM ports: — só a rede interna alcança o Spring.
    networks: [interna]

  front:
    image: ghcr.io/filipeoliveirap/domus-front:latest
    restart: unless-stopped
    environment:
      API_INTERNAL_URL: http://api:8080
    depends_on: [api]
    networks: [interna]

  redis:
    image: redis:7-alpine
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 5
    networks: [interna]

  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.14.3
    restart: unless-stopped
    environment:
      discovery.type: single-node
      xpack.security.enabled: "false"
      # Trava o heap: sem isso o ES tenta usar metade da RAM da máquina.
      ES_JAVA_OPTS: "-Xms1g -Xmx1g"
    healthcheck:
      test: ["CMD-SHELL", "curl -s http://localhost:9200/_cluster/health | grep -q '\"status\"'"]
      interval: 10s
      timeout: 5s
      retries: 10
    volumes:
      - es-data:/usr/share/elasticsearch/data
    networks: [interna]

  cloudflared:
    image: cloudflare/cloudflared:latest
    restart: unless-stopped
    command: tunnel --no-autoupdate run
    environment:
      TUNNEL_TOKEN: ${TUNNEL_TOKEN}
    depends_on: [front]
    networks: [interna]

networks:
  interna:

volumes:
  es-data:
```

- [ ] **Step 2: Criar o template de env**

Create `deploy/.env.prod.example`:

```bash
# Banco (Neon Frankfurt) — libpq/JDBC conforme o Spring espera
DATABASE_URL=jdbc:postgresql://SEU-HOST.eu-central-1.aws.neon.tech/neondb?sslmode=require
DATABASE_USERNAME=neondb_owner
DATABASE_PASSWORD=___

# JWT
JWT_SECRET=___
JWT_EXPIRATION_MS=600000
JWT_REFRESH_EXPIRATION_MS=604800000

# Cookies / proxy — LIGADOS em prod (há Cloudflare na frente)
COOKIE_SECURE=true
COOKIE_PATH_PREFIX=/api
FORWARD_HEADERS_STRATEGY=framework
RATELIMIT_TRUST_FORWARDED_FOR=true

# CORS — o domínio real
CORS_ALLOWED_ORIGINS=https://domusigreja.com.br

# Google OAuth
GOOGLE_CLIENT_ID=___

# E-mail (Resend)
EMAIL_PROVIDER=resend
EMAIL_FROM=Domus <nao-responda@domusigreja.com.br>
RESEND_API_KEY=___
FRONTEND_URL=https://domusigreja.com.br

# Sentry
SENTRY_DSN=___
SENTRY_ENVIRONMENT=prod

# Cloudflare Tunnel (gerado na Task 7)
TUNNEL_TOKEN=___
```

- [ ] **Step 3: Criar o runbook**

Create `deploy/README.md`:

```markdown
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
```

- [ ] **Step 4: Validar a sintaxe do compose**

```bash
cd /home/jos-filipe-oliveira-pereira/Documents/domus/deploy
docker compose -f docker-compose.prod.yml config >/dev/null && echo "compose OK"
```
Expected: `compose OK` (pode avisar de `TUNNEL_TOKEN` não setado — tudo bem, é só validação de sintaxe).

- [ ] **Step 5: Garantir que o .env.prod nunca vá pro git**

```bash
cd /home/jos-filipe-oliveira-pereira/Documents/domus
grep -q "deploy/.env.prod$" .gitignore || echo "deploy/.env.prod" >> .gitignore
git check-ignore deploy/.env.prod && echo "ignorado ✅"
```
Expected: `ignorado ✅`

- [ ] **Step 6: Commit**

```bash
cd /home/jos-filipe-oliveira-pereira/Documents/domus
git add deploy/docker-compose.prod.yml deploy/.env.prod.example deploy/README.md .gitignore
git commit -m "feat(deploy): docker-compose de produção + runbook

5 serviços (cloudflared, front, api, redis, elasticsearch); Postgres é o
Neon externo. api sem ports (só rede interna); cloudflared faz o
transporte seguro. ES com heap travado em 1g. .env.prod fica só na VPS."
```

---

### Task 4: GitHub Actions — buildar e publicar no ghcr.io

**Files:**
- Create: `.github/workflows/build-images.yml`

**Interfaces:**
- Produces: `ghcr.io/filipeoliveirap/domus-api:latest` e `ghcr.io/filipeoliveirap/domus-front:latest` a cada push na `main`.

- [ ] **Step 1: Criar o workflow**

Create `.github/workflows/build-images.yml`:

```yaml
name: Build e publicar imagens

on:
  push:
    branches: [main]
    paths:
      - 'backend/api/**'
      - 'frontend/**'
      - '.github/workflows/build-images.yml'
  workflow_dispatch:

permissions:
  contents: read
  packages: write   # necessário pra publicar no ghcr.io

jobs:
  build:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        include:
          - name: api
            context: backend/api
          - name: front
            context: frontend
    steps:
      - uses: actions/checkout@v4

      - name: Login no ghcr.io
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Build e push
        uses: docker/build-push-action@v6
        with:
          context: ${{ matrix.context }}
          push: true
          tags: ghcr.io/filipeoliveirap/domus-${{ matrix.name }}:latest
          # NEXT_PUBLIC_* são assados no build do front. Não são segredo (Client ID é
          # público, DSN é send-only) → vêm de repo VARIABLES, não secrets. O build da
          # api ignora estes args (Docker só avisa, não falha).
          build-args: |
            NEXT_PUBLIC_GOOGLE_CLIENT_ID=${{ vars.NEXT_PUBLIC_GOOGLE_CLIENT_ID }}
            NEXT_PUBLIC_SENTRY_DSN=${{ vars.NEXT_PUBLIC_SENTRY_DSN }}
```

Nota: `GITHUB_TOKEN` é automático (não precisa cadastrar). O `packages: write` autoriza publicar
no registry do próprio repo. **Antes de rodar**, cadastrar as duas repo variables (Settings →
Secrets and variables → Actions → **Variables**): `NEXT_PUBLIC_GOOGLE_CLIENT_ID` e
`NEXT_PUBLIC_SENTRY_DSN` (valores públicos, por isso variables e não secrets).

- [ ] **Step 2: Validar YAML**

```bash
cd /home/jos-filipe-oliveira-pereira/Documents/domus
python3 -c "import yaml; yaml.safe_load(open('.github/workflows/build-images.yml')); print('YAML OK')"
```
Expected: `YAML OK`

- [ ] **Step 3: Commit, push e abrir PR pra main**

```bash
cd /home/jos-filipe-oliveira-pereira/Documents/domus
git add .github/workflows/build-images.yml
git commit -m "ci: build e publicação das imagens no ghcr.io

Buildar fica no Actions (máquina descartável), não na VPS — a produção
só puxa. Uma imagem por serviço (api, front), publicadas a cada push na
main. GITHUB_TOKEN + packages:write, sem secret novo."
git push
```
Depois abrir PR `producao` → `main` e mergear **com merge commit**.

- [ ] **Step 4: Disparar e conferir que as imagens saíram**

```bash
gh workflow run "Build e publicar imagens" --repo Filipeoliveirap/domus --ref main
sleep 20 && gh run list --workflow="Build e publicar imagens" --repo Filipeoliveirap/domus --limit 1
```
Depois, quando concluir, confirmar os pacotes em
`https://github.com/Filipeoliveirap?tab=packages` — devem aparecer `domus-api` e `domus-front`.

⚠️ **Tornar os pacotes públicos** (ou a VPS não consegue puxar sem login): em cada pacote →
Package settings → Change visibility → Public. (Alternativa: `docker login ghcr.io` na VPS com
um PAT — mas público é mais simples pro piloto.)

---

### Task 5: Banco de produção no Neon (Frankfurt) (OPS — manual)

**Files:** nenhum no repo (muda o `.env.prod` na VPS e o secret `BACKUP_DATABASE_URL`).

**Sem migração de dado.** O banco que tem os 2073 membros é o de **DEV**; o de prod nasce
**zerado** e o Flyway monta o schema no primeiro boot do Spring. O antigo prod (São Paulo) é
descartado.

- [ ] **Step 1: Criar o projeto novo em Frankfurt**

No console do Neon → New Project → Region **AWS eu-central-1 (Frankfurt)**. Nome: `domus-prod`.
Copiar a connection string. Guardar no Bitwarden.

- [ ] **Step 2: Deixar o Flyway montar o schema (acontece no primeiro boot — Task 8)**

Nada a fazer aqui além de anotar as credenciais para o `.env.prod`. Quando o Spring subir na
VPS (Task 8), o Flyway roda as migrations `V1..V10` e cria as tabelas do zero. Conferir depois:

```bash
docker run --rm -e DST="postgresql://USER:SENHA@HOST-FRANKFURT/neondb?sslmode=require" postgres:16 \
  sh -c 'psql "$DST" -c "\dt"'
```
Expected (após a Task 8): as 9 tabelas criadas, ainda vazias (a igreja vai populá-las).

- [ ] **Step 3: Repontar o backup pro banco de PRODUÇÃO**

⚠️ Hoje o `BACKUP_DATABASE_URL` aponta pro banco de **DEV** (onde estão os 2073). Quando a
igreja começar a usar o prod, é o **prod** que precisa de backup. Trocar o secret:

```bash
printf '%s' "postgresql://USER:SENHA@HOST-FRANKFURT/neondb?sslmode=require" \
  | gh secret set BACKUP_DATABASE_URL --repo Filipeoliveirap/domus
```
**Fazer isto só depois da Task 8** (quando o schema existir), senão o backup roda contra um
banco sem tabelas. Após trocar, disparar e conferir:
```bash
gh workflow run "Backup do Postgres" --repo Filipeoliveirap/domus --ref main
```
Expected: verde, com `confirmado: ... no bucket` — o backup agora protege o prod de Frankfurt.
(Enquanto o prod estiver vazio, os backups serão pequenos; conforme a igreja usar, crescem.)

- [ ] **Step 4: Descartar o prod antigo de São Paulo**

Deletar o projeto antigo (`sa-east-1`) no console do Neon. O de **dev** continua onde está
(você usa localmente) — só não é mais alvo do backup depois do Step 3.

---

### Task 6: Provisionar e blindar a VPS (OPS — manual)

**Files:** nenhum no repo.

- [ ] **Step 1: Criar a VPS**

No Hetzner Cloud → New Server:
- Location: **Nuremberg** ou **Falkenstein** (Alemanha)
- Image: **Ubuntu 24.04**
- Type: **CX32** (4 vCPU / 8 GB) — o ES precisa da RAM
- SSH key: **adicionar a sua chave pública** (gerar com `ssh-keygen -t ed25519` se não tiver; **nunca** subir a privada)
- Name: `domus-prod`

- [ ] **Step 2: Primeiro acesso e update**

```bash
ssh root@IP-DA-VPS
apt update && apt upgrade -y
```

- [ ] **Step 3: Firewall (ufw) — fecha tudo, deixa só SSH**

```bash
apt install -y ufw
ufw default deny incoming
ufw default allow outgoing
ufw allow OpenSSH
ufw --force enable
ufw status
```
Expected: `Status: active`, só a porta 22 (SSH) liberada. **Nenhuma porta de app aberta** — o Cloudflare Tunnel dispensa (conexão de saída).

- [ ] **Step 4: SSH só por chave (mata força bruta de senha)**

Editar `/etc/ssh/sshd_config` (ou um drop-in em `/etc/ssh/sshd_config.d/`):

```
PasswordAuthentication no
PermitRootLogin prohibit-password
```
Depois:
```bash
systemctl restart ssh
```
⚠️ **Antes de fechar a sessão atual**, abra um segundo terminal e confirme que `ssh root@IP` ainda entra (por chave). Só feche a primeira quando a segunda funcionar — senão você se tranca pra fora.

- [ ] **Step 5: Atualizações de segurança automáticas**

```bash
apt install -y unattended-upgrades
dpkg-reconfigure -plow unattended-upgrades   # responder "Yes"
```

- [ ] **Step 6: Instalar Docker + compose**

```bash
curl -fsSL https://get.docker.com | sh
docker --version && docker compose version
```
Expected: as duas versões impressas.

---

### Task 7: Cloudflare Tunnel + DNS (OPS — manual)

**Files:** nenhum no repo (o token vai pro `.env.prod` da VPS).

- [ ] **Step 1: Apontar o domínio pra Cloudflare**

No registro.br, trocar os **nameservers** do `domusigreja.com.br` pelos dois que a Cloudflare
mostrar (Cloudflare → Add site → domusigreja.com.br → plano Free → ela lista os NS). A
propagação leva de minutos a algumas horas.

- [ ] **Step 2: Criar o túnel**

Cloudflare → **Zero Trust** → Networks → **Tunnels** → Create a tunnel → **Cloudflared** →
nome `domus-prod`. A tela mostra um **token** (`eyJ...`) — é o `TUNNEL_TOKEN`. **Guardar no
Bitwarden e no `.env.prod`.**

- [ ] **Step 3: Rota pública do túnel**

Ainda na config do túnel, aba **Public Hostname** → Add:
- Subdomain: (vazio) · Domain: `domusigreja.com.br`
- Service: **HTTP** → `front:3000`

(O `cloudflared` roda no mesmo compose, então enxerga o serviço `front` pela rede interna.)
Adicionar **outra** rota igual para `www` se quiser (`www.domusigreja.com.br` → mesmo serviço).

- [ ] **Step 4: SSL/TLS no modo certo**

Cloudflare → SSL/TLS → Overview → **Full (strict)**. O Tunnel já entrega criptografado; esse
modo garante que o público nunca caia em HTTP.

---

### Task 8: Primeiro deploy manual (OPS — manual)

**Files:** nenhum no repo.

- [ ] **Step 1: Levar o compose e o env pra VPS**

```bash
# do seu terminal
scp deploy/docker-compose.prod.yml root@IP-DA-VPS:/root/deploy/
scp deploy/.env.prod.example        root@IP-DA-VPS:/root/deploy/
```
Na VPS:
```bash
cd /root/deploy
cp .env.prod.example .env.prod
nano .env.prod   # preencher TUDO: Neon Frankfurt, JWT, Google, Resend, Sentry, TUNNEL_TOKEN
```

- [ ] **Step 2: Subir**

```bash
cd /root/deploy
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
docker compose -f docker-compose.prod.yml ps
```
Expected: `api`, `front`, `redis`, `elasticsearch`, `cloudflared` todos `Up` (o ES pode levar ~1 min pra ficar `healthy`).

- [ ] **Step 3: Olhar os logs de cada peça**

```bash
docker compose -f docker-compose.prod.yml logs api | tail -30       # Spring subiu, conectou no Neon?
docker compose -f docker-compose.prod.yml logs cloudflared | tail -20  # túnel conectado?
```
Expected: Spring sem stacktrace de conexão; `cloudflared` com `Registered tunnel connection`.

---

### Task 9: Verificação ponta a ponta (OPS — manual)

**Files:** nenhum (marca o roadmap ao fim).

- [ ] **Step 1: O site abre com HTTPS**

No navegador: `https://domusigreja.com.br` → a tela de login carrega, cadeado válido.

- [ ] **Step 2: Login funciona e o cookie é httpOnly**

Logar. No console: `document.cookie` **não** mostra `domus_access` nem `domus_refresh` (só
`XSRF-TOKEN`). Application → Cookies: os dois com **HttpOnly** e **Secure** marcados.

- [ ] **Step 3: A origem está mesmo travada**

```bash
# do seu terminal — tentar falar direto com a VPS (sem passar pela Cloudflare)
curl -m 5 -sik https://IP-DA-VPS/ || echo "  recusado — origem travada ✅"
```
Expected: recusa/timeout. Nenhuma porta de app aberta; só a Cloudflare alcança.

- [ ] **Step 4: A busca (Elasticsearch) responde**

Logado, usar a busca global por um membro conhecido. Deve retornar resultado (o ES indexou via
outbox). Se vier vazio, rodar a reindexação (endpoint que já existe) e conferir.

- [ ] **Step 5: O rate limiting conta pelo IP real**

```bash
# 12 logins errados seguidos — deve travar por IP (CF-Connecting-IP), não pelo IP do túnel
for i in $(seq 1 12); do
  curl -s -o /dev/null -w "%{http_code} " -X POST https://domusigreja.com.br/api/auth/login \
    -H 'Content-Type: application/json' -d '{"email":"x@x.com","senha":"errada"}'
done; echo
```
Expected: os primeiros 400/401 e, ao passar do limite, **429**. (Se travasse o mundo inteiro
de uma vez, seria sinal de que o `CF-Connecting-IP` não está sendo lido — a Task 1.)

- [ ] **Step 6: O backup ainda protege o banco em uso**

```bash
gh run list --workflow="Backup do Postgres" --repo Filipeoliveirap/domus --limit 1 \
  --json conclusion,createdAt -q '.[] | "  " + .conclusion + "  " + .createdAt'
```
Expected: último run `success`. (Já foi repontado pro Frankfurt na Task 5.)

- [ ] **Step 7: Marcar no roadmap**

Em `backend/api/CLAUDE.md`, marcar hospedagem como FEITA (VPS Hetzner + Neon Frankfurt +
Cloudflare Tunnel, ~R$50/mês, deploy manual), e registrar que **CI/CD automatizado** é o
próximo item. Commit + PR pra main (merge commit).

---

## Verificação final

- [ ] `mvn -o test` → PASS (46 testes)
- [ ] `https://domusigreja.com.br` abre com HTTPS válido
- [ ] `document.cookie` não expõe os cookies de sessão
- [ ] A VPS **não** responde direto (só via Cloudflare)
- [ ] Busca funciona; rate limiting trava por IP real; backup verde
- [ ] `git status` limpo, nenhum `.env`/segredo commitado
- [ ] Roadmap atualizado; CI/CD marcado como próximo
