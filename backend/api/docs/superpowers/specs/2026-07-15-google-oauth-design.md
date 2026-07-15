# Google OAuth (login + cadastro) — Design

**Data:** 2026-07-15
**Fase:** 1 (Fundações, autenticação e endurecimento de produção)
**Objetivo:** Fechar o modelo de autenticação híbrido do roadmap, adicionando "Entrar com
Google" e "Cadastrar minha igreja com Google" ao lado do login/senha nativo que já existe.

---

## 1. Contexto e decisões

O Domus já tem login nativo (bcrypt), refresh token com rotação/revogação, e reset de senha.
Falta a metade Google do modelo híbrido. As decisões abaixo foram fechadas no brainstorming:

| # | Decisão | Escolha |
|---|---------|---------|
| 1 | Fluxo de integração | **ID token no front (Google Identity Services)** — o botão roda no Next.js, o Google devolve um ID token, o backend só valida a assinatura e emite nossa sessão. Sem Authorization Code, sem `client_secret`, sem callback. |
| 2 | Login vs cadastro | **Dois fluxos separados** — botão "Entrar com Google" (login) e "Cadastrar minha igreja com Google" (cadastro), cada um com seu endpoint. |
| 3 | Schema p/ contas só-Google | **`senha_hash` nullable + coluna `google_sub`** (ID imutável do Google, único). |
| 4 | Escopo | **Cenários 1 e 3** (login de usuário existente + cadastro de igreja nova). Cenário 2 (membro provisionado sem usuário) não existe hoje: o `concederAcesso` já cria o `usuario` completo com senha + role. |

**Por que ID token e não Authorization Code:** só precisamos de **identidade**, não de acesso
contínuo a APIs do Google. Quando o Google Calendar entrar (scope futuro), será um módulo
**separado** de autorização de API (Code flow com `offline` + escopo `calendar`, guardando o
refresh token do Google por usuário) — que seria necessário de qualquer forma, independentemente
de como o login foi feito. Fazer Code flow agora adicionaria complexidade sem adiantar o Calendar
(menor privilégio: não se pede escopo de agenda numa tela de login).

**Ponte de identidade:** o `membro.email` (único) continua sendo a chave que liga a identidade
Google a um usuário. O `google_sub` é uma robustez adicional (sobrevive a troca de e-mail na
conta Google).

---

## 2. Bibliotecas

- **Back — `google-api-client` (`GoogleIdTokenVerifier`):** valida o ID token (assinatura contra
  as chaves públicas do Google com cache/rotação automática, `aud` = nosso Client ID, `iss`,
  validade). Alternativa descartada: validar na mão com `java-jwt` + baixar o JWKS — reinventa
  roda testada do Google na parte mais sensível (segurança).
- **Front — `@react-oauth/google`:** wrapper React fino sobre o Google Identity Services; fornece
  o botão e a entrega do ID token. Alternativa descartada: script GIS cru — mais boilerplate, sem
  ganho.

---

## 3. Pré-requisito de setup (Google Cloud Console) — FEITO

- Projeto criado no Google Cloud Console.
- OAuth consent screen: **External**, em modo Testing, com o e-mail do autor como test user.
- OAuth Client ID (Web application), origem autorizada `http://localhost:3000`.
- **Client ID gerado:** `1006320938803-sbqitjuq96r07cog7s77a9h4i1bdursh.apps.googleusercontent.com`
  (público — vai no `.env` do back e em `NEXT_PUBLIC_GOOGLE_CLIENT_ID` no front). Client secret
  **não é usado** neste fluxo.

---

## 4. Cenários de identidade

| Cenário | Situação do e-mail | Comportamento |
|---------|--------------------|---------------|
| **1. Login** | Já existe `usuario` (self-signup admin OU provisionado — ambos já têm `usuario`) | Entra: emite nosso JWT + refresh |
| **2. Provisionado sem usuário** | N/A hoje — `concederAcesso` já cria o `usuario` | Fora de escopo; revisitar se o fluxo de convite futuro criar membro sem usuário |
| **3. Cadastro** | E-mail desconhecido, via botão de cadastro | Cria igreja + membro + usuário ADMIN_IGREJA (sem senha) |
| **Desconhecido no login** | E-mail sem conta, via botão de login | Erro `CONTA_NAO_ENCONTRADA` (barrado de propósito) |

---

## 5. Fluxo de LOGIN (cenário 1)

1. **Front:** clique em "Entrar com Google" → popup → Google devolve o **ID token**.
2. **Front → Back:** `POST /auth/google/login` com `{ idToken }`.
3. **Back — `GoogleAuthService.login(idToken)`:**
   - Valida o ID token (`GoogleIdTokenVerifier`): assinatura, `aud` = Client ID, validade.
   - Exige `email_verified == true` — senão `TOKEN_GOOGLE_INVALIDO`.
   - Extrai `sub`, `email`, `nome`.
   - **Encontra o usuário:** primeiro por `google_sub`; se não achar, por `email` — e nesse caso
     **grava o `google_sub`** no usuário (vínculo na primeira vez).
   - Se não achar → `CONTA_NAO_ENCONTRADA`.
   - Se achar → checa `ativo`, `registrarLogin()`, e daqui em diante **idêntico ao login nativo**:
     `tokenService.generateToken(usuario)` + `refreshTokenService.criar(usuario.getId())`.
4. **Back → Front:** devolve o **mesmo `LoginResponseDTO`** do login nativo. Front guarda tokens e
   vai pro `/inicio`.

**Reuso:** o Google só identifica a pessoa. Emitida a identificação, refresh/rotação/revogação/
roles/`igreja_id` funcionam exatamente como no nativo.

---

## 6. Fluxo de CADASTRO (cenário 3)

Dois momentos: o Google identifica a pessoa; o formulário completa os dados da igreja (que o
Google não fornece).

1. **Front:** clique em "Cadastrar minha igreja com Google" → popup → **ID token**.
2. **Front — completa dados:** usa o token pra pré-preencher nome/e-mail (read-only) e pede
   **nome da igreja** (obrigatório), CNPJ, telefone (mesmos campos do cadastro nativo).
3. **Front → Back:** `POST /auth/google/registrar` com `{ idToken, nomeIgreja, cnpj, telefoneContato, ... }`.
4. **Back — `GoogleAuthService.registrar(...)`:**
   - Valida o ID token (assinatura, `aud`, `email_verified == true`). **A identidade (nome/e-mail)
     vem SEMPRE do token validado, nunca do corpo da requisição.**
   - Anti-duplicidade: se já existe `membro`/`usuario` com o e-mail → `EMAIL_DUPLICADO`.
   - Cria igreja + membro + usuário ADMIN_IGREJA com `senha_hash = null` e `google_sub` preenchido.
   - Emite JWT + refresh.
5. **Back → Front:** devolve `RegistrarIgrejaResponse` → front guarda e vai pro `/inicio`.

**Refatoração incluída:** extrair a criação de igreja+membro+admin do `IgrejaService.registrar`
para um método reutilizável (ex.: `criarIgrejaComAdmin(dadosIgreja, dadosAdmin, senhaHashOuNull, googleSub)`),
chamado pelos dois caminhos (nativo e Google). Evita duas cópias da mesma regra de negócio.

---

## 7. Mudança no login nativo + schema

### 7.1 Migration Flyway (nova)

```sql
ALTER TABLE usuario ALTER COLUMN senha_hash DROP NOT NULL;
ALTER TABLE usuario ADD COLUMN google_sub VARCHAR(255);
CREATE UNIQUE INDEX ux_usuario_google_sub ON usuario (google_sub);
```

- `senha_hash` nullable → contas só-Google não têm senha.
- `google_sub` nullable + índice único → um mesmo Google não vincula em duas contas; busca por
  `sub` rápida. `UNIQUE` no Postgres permite múltiplos NULLs → usuários nativos convivem.

Reflexo no código: `Usuario.senhaHash` vira `nullable = true`; adiciona campo `googleSub`.

### 7.2 Login nativo defende-se de conta sem senha

Antes de chamar o `authenticationManager`, o `AuthService.login` verifica: se o usuário existe
mas tem `senha_hash == null`, lança `CONTA_SEM_SENHA` (não deixa o `passwordEncoder.matches(senha, null)`
acontecer). Mensagem orienta a entrar com Google ou definir senha (via fluxo de reset existente).

### 7.3 O que NÃO muda

Usuários nativos existentes (`google_sub = null`, `senha_hash` preenchido) logam igual. Refresh,
rotação, revogação, roles, `igreja_id` no JWT e soft delete: intocados.

---

## 8. Frontend

- Instalar `@react-oauth/google`; envolver o layout de auth com `<GoogleOAuthProvider clientId={NEXT_PUBLIC_GOOGLE_CLIENT_ID}>`.
- **Login:** botão "Entrar com Google" abaixo do divisor "OU". Sucesso → mesmo caminho do nativo.
  - `CONTA_NAO_ENCONTRADA` → mensagem completa: "Não encontramos uma conta vinculada a este Google.
    Se você é responsável por uma igreja, cadastre-a primeiro. Se você é membro de uma igreja já
    cadastrada, peça ao administrador dela para conceder seu acesso."
  - `CONTA_SEM_SENHA` (do login nativo) → aviso + **dois botões**: "Entrar com Google" e "Definir
    senha" (→ `/forgot-password` com o e-mail digitado pré-preenchido).
- **Cadastro:** botão "Cadastrar minha igreja com Google" → formulário com nome/e-mail read-only
  (do token) + campos da igreja. `EMAIL_DUPLICADO` → "Este Google já tem conta, faça login."
- Camada de serviço: `endpoints.ts` (`GOOGLE_LOGIN`, `GOOGLE_REGISTRAR`), `auth.service.ts`
  (`googleLogin`, `googleRegistrar`), `auth.types.ts` (tipos; reusa `LoginResponse`/`RegistrarIgrejaResponse`).

---

## 9. Códigos de erro

| Código | Quando | HTTP |
|--------|--------|------|
| `TOKEN_GOOGLE_INVALIDO` | assinatura/`aud`/expiração inválidos, ou `email_verified=false` | 401 |
| `CONTA_NAO_ENCONTRADA` | login Google, e-mail sem conta | 409 |
| `CONTA_SEM_SENHA` | login nativo numa conta só-Google | 409 |
| `EMAIL_DUPLICADO` | cadastro Google, e-mail já existe | 409 |

---

## 10. Testes

**Back (foco), com `GoogleIdTokenVerifier` mockado:**
- token inválido → `TOKEN_GOOGLE_INVALIDO`
- `email_verified=false` → `TOKEN_GOOGLE_INVALIDO`
- login acha por `google_sub` → sessão emitida
- login acha por `email` e grava o `google_sub` → sessão emitida + `sub` persistido
- login não acha → `CONTA_NAO_ENCONTRADA`
- cadastro cria igreja + membro + admin com `senha_hash = null` e `google_sub`
- cadastro com e-mail duplicado → `EMAIL_DUPLICADO`
- login nativo em conta sem senha → `CONTA_SEM_SENHA`

**Manual:** fluxo real com a conta Google de teste (login, cadastro, conta só-Google tentando
login nativo).

---

## 11. Fora de escopo (anotado)

- **Google Calendar / autorização de API:** módulo futuro separado (Code flow + `offline` +
  escopo `calendar`).
- **Fluxo de convite** (admin escolhe role e convida por e-mail; usuário define a própria senha):
  mudança futura no provisionamento. Quando existir, pode criar membro/usuário sem senha — e o
  Google OAuth já estará pronto pra recebê-los (por isso `senha_hash` nullable agora).
- **Publicar o app no Google** (sair do modo Testing): quando o piloto for ao ar (Fase 5).
