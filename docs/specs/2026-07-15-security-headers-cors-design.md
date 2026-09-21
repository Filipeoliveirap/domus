# Security Headers + CORS — Design

**Data:** 2026-07-15
**Fase:** 1 (endurecimento de produção)
**Objetivo:** Fechar o gap de security headers e tornar o CORS configurável por ambiente,
no backend (API) e no frontend (Next.js).

---

## 1. Backend (API REST — só JSON)

### 1.1 CORS via env
- Trocar o `http://localhost:3000` hardcoded por propriedade `app.cors.allowed-origins`
  (lista separada por vírgula), lida de env `CORS_ALLOWED_ORIGINS` com default
  `http://localhost:3000`. Em produção, setar a env com o domínio real.
- Mantém: métodos (GET/POST/PUT/PATCH/DELETE/OPTIONS), `allowedHeaders("*")`,
  `allowCredentials(true)`.

### 1.2 Security headers (bloco `.headers(...)` no SecurityFilterChain)
- **HSTS**: `Strict-Transport-Security`, maxAge 1 ano, includeSubDomains. Só atua em HTTPS
  (inofensivo em dev http localhost).
- **`X-Frame-Options: DENY`** (explícito; já era default do Spring Security).
- **`X-Content-Type-Options: nosniff`** (explícito; já era default).
- **`Referrer-Policy: strict-origin-when-cross-origin`** (novo).
- **CSP da API**: `default-src 'none'; frame-ancestors 'none'`. A API só devolve JSON, então
  o navegador não deve carregar recurso algum a partir dela — CSP máxima e segura aqui.

---

## 2. Frontend (Next.js — serve HTML)

`next.config.ts` → função `async headers()` aplicando a todas as rotas (`source: '/:path*'`):

- `Strict-Transport-Security` (prod), `X-Content-Type-Options: nosniff`,
  `Referrer-Policy: strict-origin-when-cross-origin`, `X-Frame-Options: DENY`.
- `Permissions-Policy: camera=(), microphone=(), geolocation=()`.
- **Content-Security-Policy** (pragmática — libera o Google Identity, restringe origens):

```
default-src 'self';
script-src 'self' 'unsafe-inline' 'unsafe-eval' https://accounts.google.com https://accounts.google.com/gsi/client;
style-src 'self' 'unsafe-inline' https://accounts.google.com/gsi/style;
frame-src https://accounts.google.com;
connect-src 'self' <NEXT_PUBLIC_API_URL> https://accounts.google.com;
img-src 'self' data: https://*.googleusercontent.com https://accounts.google.com;
font-src 'self';
base-uri 'self';
form-action 'self';
frame-ancestors 'none';
object-src 'none';
```

- `accounts.google.com` em script/frame/connect → mantém o botão do Google funcionando.
- `<NEXT_PUBLIC_API_URL>` em connect-src → o axios falar com o backend (localhost:8080 em dev,
  domínio da API em prod). Lido de `process.env.NEXT_PUBLIC_API_URL` no build do next.config.
- `googleusercontent.com` em img-src → fotos de perfil do Google.

**Trade-off consciente:** `'unsafe-inline'`/`'unsafe-eval'` são concessão ao Next.js sem
CSP baseada em nonce. Registrado como dívida no BACKLOG (hardening = nonce por requisição).

---

## 3. Verificação (sem TDD — é config)

- **Back:** `curl -I` num endpoint público → conferir HSTS/X-Frame-Options/nosniff/Referrer-Policy/CSP.
  Confirmar que o CORS ainda deixa `http://localhost:3000` passar (preflight OPTIONS).
- **Front:** carregar `/login` e `/cadastro`, conferir no DevTools (Network → Response Headers)
  que a CSP está presente e que o **botão do Google continua funcionando** (login + cadastro).

---

## 4. Fora de escopo (no BACKLOG)
- CSP baseada em nonce (remover `unsafe-inline`/`unsafe-eval`).
