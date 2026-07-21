# Domus — Roadmap da Versão de Produção

> Planejamento para evoluir o Domus do escopo acadêmico (TCC) para uma versão de
> produção, começando pelo piloto na igreja do autor e preparando o terreno para o
> lançamento comercial. Escrito para servir de **contexto e guia ao trabalhar com o
> Claude Code**. 

## Modo de trabalho: mentoria (instrução para o Claude Code)

O autor está aprendendo engenharia de software e **não quer só ver código pronto** — quer
entender tudo. Em cada decisão e cada implementação, aja como um **mentor/professor**, não
apenas como executor:

- **Antes de escrever código,** explique o plano: o que vai ser feito, por quê, quais
  conceitos estão envolvidos, quais bibliotecas/APIs serão usadas e o **motivo** da escolha
  (e quais alternativas foram descartadas, e por quê).
- **Explique o fluxo de ponta a ponta:** como a requisição entra, passa pelas camadas
  (controller → service → repository) e volta; o que cada parte faz.
- **Explique a lógica e as libs, não só o resultado.** Quando surgir um conceito novo,
  ensine como um professor ensinaria a um aluno, usando **analogias** quando ajudar.
- **Vá um passo por vez** e confirme o entendimento antes de seguir. Prefira ensinar bem a
  entregar rápido.
- Objetivo final: o autor precisa **entender o suficiente para manter e evoluir sozinho**
  cada coisa construída. Trate como pareamento (pair programming), não como entrega.

---

## Como usar este documento

- Isto é um **roadmap**, não uma especificação técnica detalhada. O fluxo completo de
  cada feature será desenhado com o Claude Code na hora de implementar.
- Trabalhe **uma feature por vez**, na ordem das fases. Cada fase tem um objetivo e um
  critério de "pronto".
- Sugestão prática: mantenha um `CLAUDE.md` na raiz do repositório com o contexto fixo
  do projeto (stack, convenções, arquitetura) e aponte o Claude Code para **este**
  roadmap quando for planejar cada item. Assim ele não precisa reler tudo toda vez.
- A seção **Decisões já tomadas** (no final) são *guardrails*: já foram debatidas e não
  precisam ser rediscutidas a cada feature.

---

## Contexto do projeto

Domus é um SaaS **multi-inquilino (multi-tenant)** de gestão administrativa de igrejas
de pequeno e médio porte. Módulos atuais (herdados do TCC): autenticação + recuperação
de senha, usuários, membros, eventos, financeiro (com categorias e relatórios) e busca
global unificada.

**Stack — Back:** Java 21, Spring Boot, Spring Security, PostgreSQL (fonte da verdade),
Spring Data JPA, Flyway (migrations), Redis (cache), Elasticsearch (busca, sincronizada
via *transactional outbox*).

**Stack — Front:** Next.js, TypeScript, CSS Modules, TanStack Query, React Hook Form + Zod.

**Convenções e padrões vigentes:**
- Isolamento lógico por `igreja_id` em toda entidade do domínio, **sempre extraído do
  JWT, nunca do corpo da requisição** (defesa contra acesso cruzado entre igrejas).
- Camadas `controller → service → repository`; services retornam **DTOs**, nunca
  entidades de persistência.
- **Soft delete** (`deleted_at`) nas entidades.
- Perfis de acesso: `ADMIN_IGREJA`, `LIDER`, `MEMBRO`.
- Relação central: todo **usuário** (credencial de acesso) está vinculado a exatamente
  um **membro** (pessoa). Nem todo membro tem usuário. `membro.email` é **único**.
- **Responsividade é obrigatória em toda feature de front.** Toda funcionalidade nova
  (tela, formulário, modal, drawer, tabela) tem que ser ajustada para **mobile** como
  parte da própria entrega — não é etapa separada nem opcional. Padrões já usados:
  tabelas viram **cards** no mobile; headers com título+botão **empilham**; grids de
  formulário **colapsam** para 1 coluna; modais/drawers reduzem padding; `min-width: 0`
  na cadeia flex/grid e larguras fixas (ex.: botão Google) revistas para evitar overflow
  horizontal. Validar no viewport de celular antes de considerar pronto.

---

## Modelo de dados (diagrama ER)

> **Fonte da verdade são as migrations** (`src/main/resources/db/migration`), não este
> diagrama. Ao mexer no schema, atualize aqui também. Estado atual: **V16**.
> Campos de rotina (`created_at`, `updated_at`, `deleted_at`) foram omitidos por ruído,
> exceto quando têm significado (soft delete).

```mermaid
erDiagram
    IGREJA ||--o{ IGREJA : "é sede de (igreja_mae_id)"
    IGREJA ||--o{ MEMBRO : tem
    IGREJA ||--o{ USUARIO : tem
    IGREJA ||--o{ EVENTO : tem
    IGREJA ||--o{ CATEGORIA_FINANCEIRA : tem
    IGREJA ||--o{ MOVIMENTACAO_FINANCEIRA : tem
    IGREJA ||--o{ INSCRICAO_EVENTO : tem
    MEMBRO ||--o| USUARIO : "pode ter (1-1)"
    MEMBRO ||--o{ INSCRICAO_EVENTO : "se inscreve em"
    ROLE   ||--o{ USUARIO : define
    CATEGORIA_FINANCEIRA ||--o{ MOVIMENTACAO_FINANCEIRA : classifica
    MEMBRO ||--o{ MOVIMENTACAO_FINANCEIRA : "atribuída a"
    USUARIO ||--o{ MOVIMENTACAO_FINANCEIRA : "criou/atualizou"
    USUARIO ||--o{ INSCRICAO_EVENTO : "inscreveu"
    USUARIO ||--o{ IGREJA : "atualizou/vinculou"
    EVENTO ||--o{ INSCRICAO_EVENTO : "tem"
    INSCRICAO_EVENTO ||--o{ ACOMPANHANTE_INSCRICAO : "pode ter"

    IGREJA {
        uuid      id PK
        uuid      igreja_mae_id FK "V12 - NULL=independente; 2 níveis"
        varchar   codigo_vinculo UK "V12 - 8 chars XK4P-2M7Q"
        timestamp codigo_gerado_em "V13 - não expira; permite sugerir rotação"
        timestamp vinculado_em "V13"
        uuid      vinculado_por_usuario_id FK "V13 - quem digitou o código"
        varchar   nome
        varchar   razao_social "V13 - par do CNPJ p/ nota fiscal"
        varchar   cnpj
        varchar   denominacao "V13"
        varchar   email "contato"
        varchar   telefone
        varchar   logo_url "V13"
        varchar   plano
        varchar   cep_logradouro_numero "V13 - endereço estruturado"
        varchar   complemento_bairro_cidade_uf "V13"
        uuid      atualizado_por_usuario_id FK "V13 - logs de atividade"
    }

    MEMBRO {
        uuid      id PK
        uuid      igreja_id FK "isolamento multi-tenant"
        varchar   nome
        varchar   email UK "único - vira a chave de login"
        varchar   telefone
        date      data_nascimento
        varchar   status "ATIVO|INATIVO|VISITANTE"
        varchar   estado_civil
        varchar   ministerio
        varchar   foto
        varchar   cep_logradouro_numero "V11 - endereço estruturado"
        varchar   complemento_bairro_cidade_uf "V11"
        boolean   batizado "V15"
        date      data_batismo "V15"
        timestamp deleted_at "soft delete"
    }

    USUARIO {
        uuid      id PK
        uuid      igreja_id FK
        uuid      membro_id FK,UK "1-1: todo usuário é um membro"
        uuid      role_id FK
        varchar   senha_hash "nullable desde V10 (conta só-Google)"
        varchar   google_sub UK "V10"
        boolean   ativo
        timestamp ultimo_login_em
        timestamp delete_at "soft delete"
    }

    ROLE {
        uuid    id PK
        varchar nome UK "ADMIN_IGREJA|LIDER|MEMBRO"
        varchar descricao
    }

    EVENTO {
        uuid      id PK
        uuid      igreja_id FK
        varchar   titulo
        text      descricao
        timestamp inicio_em
        timestamp fim_em "NULL = sem fim declarado"
        varchar   local
        varchar   foto
        integer   vagas "V15 - NULL = sem limite"
        numeric   preco "V15 - NULL = gratuito"
        boolean   exclusivo_membros "V15"
        boolean   exclusivo_batizados "V15"
        boolean   requer_inscricao "V16"
        timestamp deleted_at "soft delete"
    }

    INSCRICAO_EVENTO {
        uuid      id PK
        uuid      igreja_id FK "isolamento multi-tenant"
        uuid      evento_id FK
        uuid      membro_id FK
        uuid      inscrito_por_usuario_id FK "V15 - NULL = auto-inscrição"
        varchar   status "CONFIRMADA|CANCELADA"
    }

    ACOMPANHANTE_INSCRICAO {
        uuid      id PK
        uuid      inscricao_id FK "ON DELETE CASCADE"
        varchar   nome
        varchar   telefone "V15 - opcional"
    }

    CATEGORIA_FINANCEIRA {
        uuid      id PK
        uuid      igreja_id FK
        varchar   nome "único por igreja (case-insensitive, V7)"
        varchar   tipo "ENTRADA|SAIDA|AMBOS"
        timestamp deleted_at "soft delete"
    }

    MOVIMENTACAO_FINANCEIRA {
        uuid      id PK
        uuid      igreja_id FK
        uuid      categoria_id FK
        uuid      membro_id FK "opcional - atribuinte"
        uuid      criado_por_usuario_id FK
        uuid      atualizado_por_usuario_id FK
        varchar   tipo "ENTRADA|SAIDA"
        numeric   valor "CHECK > 0"
        date      data_movimentacao
        text      descricao
        timestamp deleted_at "soft delete"
    }

    OUTBOX {
        uuid    id PK
        varchar tipo_entidade
        uuid    entidade_id
        varchar operacao
        boolean processado "transactional outbox p/ Elasticsearch"
    }
```

### O que ler neste diagrama

- **`igreja_id` em toda entidade de domínio** é o isolamento multi-tenant. Vem sempre do
  JWT, nunca do corpo da requisição.
- **A auto-relação de `IGREJA`** (`igreja_mae_id`) é a hierarquia sede↔congregações. Uma
  congregação **é** uma igreja que tem mãe — por isso as checagens de isolamento
  continuam valendo sem alteração. **Regra dos 2 níveis:** quem tem mãe não pode ser mãe.
- **`MEMBRO ||--o| USUARIO`** é 1-para-1 opcional: todo usuário tem um membro; nem todo
  membro tem usuário (só quem recebeu acesso).
- **`igreja` referencia `usuario` e vice-versa.** As FKs circulares são intencionais e
  seguras porque as de `igreja → usuario` são todas nuláveis (auditoria).
- **Inscrição em evento (V15–V16):** uma inscrição pertence a um membro e a um evento.
  Vagas contam **pessoas** (inscritos confirmados + seus acompanhantes), não inscrições.
  Acompanhantes (`acompanhante_inscricao`) existem apenas para quem NÃO é membro da
  igreja e servem para saber "de onde essa pessoa veio". Cancelamento é mudança de status
  (preserva histórico de quem inscreveu quem); reinscricão reaproveita a mesma linha
  graças ao `UNIQUE (evento_id, membro_id)`. O `requer_inscricao` (V16) é o master toggle
  que separa evento que se organiza de evento que só acontece.

---

## Princípios norteadores

1. **Igreja = design partner, não primeiro cliente comercial.** É um *soft opening*:
   onboarding na mão, sem self-service nem cobrança. O objetivo é observar uso real e
   aprender antes de escalar.
2. **MVP é mínimo *viável*.** Entregar a menor coisa que gera valor real e destrava
   aprendizado. Toda feature construída antes do uso real é construída no escuro.
3. **Construir o mínimo, depois observar.** Adicionar campos e filtros com base em uso
   real, não em suposição (YAGNI).
4. **Build vs. buy.** Não reinventar o que provedores maduros já fazem (pagamento,
   e-mail, SMS). Integrar, não construir do zero.
5. **Fundações e segurança antes de dado real.** Backup, e-mail, rastreamento de erro,
   modelo de autenticação e correções de segurança precisam existir **antes** de a igreja
   entrar de verdade.

---

## Fases

### Fase 1 — Fundações, autenticação e endurecimento de produção

> **Objetivo:** deixar o ambiente seguro, observável e com o **modelo de autenticação
> definido**, antes de qualquer dado real de igreja entrar. Quase nada aqui é "feature
> visível", mas tudo é pré-requisito — inclusive a auth, que é a única porta de entrada
> do sistema.

> **Progresso (atualizado em 2026-07-15):** ver detalhes na memória do projeto
> (`refresh-token-familia-auth`, `email-transacional-reset-senha`). Tudo em branch `producao`.

- [x] **Modelo de autenticação: híbrido (Google OAuth + e-mail/senha nativo)** — **FEITO**:
  nativo + reset de senha ✅ e Google OAuth (login + cadastro) ✅.
- Decisão: duas formas de entrar, que convivem — "Entrar com Google" (OAuth) e
  e-mail/senha nativo (que JÁ existe e funciona, com proteções tipo bcrypt).
- [x] Falta no nativo: função "esqueci minha senha" (reset via link por e-mail) — **FEITO**
  (endpoints `/auth/forgot-password` e `/auth/reset-password` + telas no front).
- [x] Login E cadastro com Google (OAuth) — **FEITO** (endpoints `/auth/google/login` e
  `/auth/google/registrar`; ID token validado com `GoogleIdTokenVerifier`; `senha_hash`
  nullable + `google_sub` único; login nativo barra conta só-Google com `CONTA_SEM_SENHA`;
  botões no front de login e cadastro). Ver spec/plano em `docs/superpowers/`.
- Entra de novo: login E cadastro com Google. No cadastro, o Google cria igreja +
  primeiro membro + primeiro usuário (ADMIN_IGREJA) já com e-mail e nome verificados.
- Provisionamento (admin dá acesso) ≠ login. Depois de provisionado, o membro entra por:
  (a) Google — vínculo pelo e-mail (membro.email é único), primeiro login verifica posse;
  (b) Nativo — precisa definir uma senha antes, reusando o MESMO fluxo do reset.
- Sessão: nos dois caminhos, após identificar a pessoa (token Google ou bcrypt), o
  backend emite os próprios JWT + refresh. Logo, refresh/revogação e rate limiting valem
  para ambos.

- [x] **E-mail transacional** (Resend) — **FEITO e validado** (envio real confirmado).
    - *Back:* `EmailService` (interface) + `LogEmailService`/`ResendEmailService` chaveados
      por `email.provider`. Falta: verificar domínio no Resend (hoje só envia p/ o dono da
      conta no sandbox).
    - *Front:* estado "e-mail enviado" na tela `/forgot-password` ✅.

- [x] **Backup automático do Postgres** — **FEITO e validado ao vivo** (2026-07-17):
    - *Motivação real:* o **Neon Free dá só 6h de PITR**. O dump externo não é a segunda
      rede de segurança — é a **única**. E backup que mora no mesmo provedor que o dado é
      redundância, não backup.
    - *Como:* workflow diário (`0 6 * * *` UTC = 03:00 BRT) → `pg_dump -Fc` → **teste de
      restauração** num Postgres 16 descartável comparando a contagem de **cada tabela**
      contra a origem → criptografia **`age` assimétrica** (só a chave pública no CI: ele
      escreve e não lê) → **Cloudflare R2** (`domus-backups`, retenção de 90 dias via
      lifecycle rule). Lógica em `scripts/backup-postgres.sh` — roda local, porque workflow
      só se testa empurrando commit.
    - *Alerta:* **Sentry Crons** como dead man's switch + issue alert `Backup do Postgres
      falhou`. **A regra padrão do projeto NÃO servia** (filtra "high priority" e o issue de
      cron não entra) — descoberto quebrando de propósito. O alerta precisa dos **dois**
      gatilhos: `new issue` **e** `resolved becomes unresolved` — o issue auto-resolve
      quando o backup volta, então as falhas seguintes são **reaberturas**, e só o primeiro
      gatilho avisaria uma única vez na vida.
    - *Validado provocando as falhas:* dump truncado → o teste reprova; secret quebrado →
      job falha, check-in `error`, issue, **e-mail confirmado na caixa**. E o ensaio manual
      completo: baixar do R2, descriptografar com a chave privada, restaurar, conferir.
    - ⚠️ **A chave privada `age` é ponto único de falha** (Bitwarden + cópia offline).
    - ⚠️ **Ensaio manual trimestral** (`scripts/ensaio-restauracao.sh`) no calendário — a
      automação **não** prova que o arquivo abre com a sua chave; o CI não tem a privada.
    - Ver spec/plano em `docs/superpowers/`.

- [x] **Rastreamento de erro (Sentry) + logs estruturados** — **FEITO** (2026-07-16):
    - *Back:* `sentry-spring-boot-starter-jakarta` (DSN por env, só captura 500, scrub de PII);
      logs estruturados (`logback-spring.xml`: JSON em prod / console+MDC em dev) com
      `RequestIdFilter` (`request_id` + header `X-Request-Id`) e `usuario_id`/`igreja_id` no MDC.
    - *Front:* `@sentry/nextjs` (instrumentation client+server, DSN por env, scrub); CSP libera `*.sentry.io`.
    - *Falta ligar:* criar conta no sentry.io e plugar `SENTRY_DSN` (back) e `NEXT_PUBLIC_SENTRY_DSN` (front).

- **Correções de segurança (as "nuances")**
    - [x] **Refresh token + revogação:** **FEITO** — refresh opaco no Redis, rotação,
      revogação por logout e **detecção de reuso** (famílias de token). Access token 10 min.
      Bônus: corrigido o bug do contador de tentativas de login (nunca bloqueava) e o
      backend agora devolve **401** (não 403) p/ token ausente/expirado (destrava o refresh no front).
    - [x] **Rate limiting em todos os endpoints** (não só no login) — **FEITO** (2026-07-16):
      `RateLimitFilter` (janela fixa no Redis, global 100/min + auth 10/min por IP, 429 +
      `Retry-After`, `X-Forwarded-For` sob flag) e `LoginAttemptService` migrado p/ Redis.
      Limites por env (`app.ratelimit.*`). Validado ao vivo (curl → 429).
    - [x] **Segredos em variáveis de ambiente** — já em uso (`.env`, gitignored).
    - [x] **CORS restrito + security headers** — **FEITO** (commit `f607b0f`): CORS por env
      (`app.cors.allowed-origins`) e security headers no back (HSTS, X-Frame-Options, nosniff,
      Referrer-Policy, CSP) e no front (`next.config.ts`).
    - [x] **Token fora do `localStorage` (XSS) + CSRF reativado** — **FEITO e validado ao vivo**
      (2026-07-16): a sessão vive em cookies `httpOnly`+`Secure`+`SameSite=Lax` emitidos pelo
      backend (`domus_access` 10 min `Path=/api`; `domus_refresh` 7 dias `Path=/api/auth`).
      Saíram o `persist` do zustand, o `localStorage.setItem` e o `document.cookie` por JS —
      **o localStorage não participa mais da autenticação** (nem o `id`). **CSRF double-submit
      reativado** junto, como o modelo de cookie exige. Entrou `GET /auth/me` (o servidor virou
      dono da verdade da sessão; de quebra mata a role velha em cache) e o front passou a falar
      com a API por um **proxy same-origin** (`/api/*` no Next), o que desacopla o cookie da
      decisão de hospedagem. Validado no navegador: `document.cookie` mostra só `XSRF-TOKEN`
      (legível por design) e `g_state` (do Google), sem os cookies de sessão.
      **⚠️ Requisito de deploy que isso criou:** precisa de um proxy reverso real na frente do
      Next (`X-Forwarded-For`/`Proto`) + `RATELIMIT_TRUST_FORWARDED_FOR=true` +
      `FORWARD_HEADERS_STRATEGY=framework`, senão o rate limiting por IP vira um balde único.
      Ver spec/plano em `docs/superpowers/` e os resíduos no BACKLOG.
    - [x] **Vulnerabilidades de dependência (front)** — **FEITO** (2026-07-16, commit `ae95bb8`):
      `npm audit` saiu de 7 (1 baixa, 3 médias, 3 altas) para **0**. Como: `npm audit fix` (sem
      `--force`, que rebaixaria o Next p/ 9.3.3 e quebraria o build), `next@16.2.10` explícito e
      `overrides.postcss ^8.5.15`. **Falta:** avaliar o back (ex.: OWASP dependency-check).
    - [ ] Revisão de **validação de input** em toda entrada — contínuo.
    - [x] **Verificar domínio no Resend** — **FEITO** (2026-07-18): domínio `domusigreja.com.br`
      verificado (DKIM + SPF/MX no subdomínio `send`, via DNS na Cloudflare). Remetente de
      produção `Domus <nao-responda@domusigreja.com.br>` (env `EMAIL_FROM`). Testado ao vivo:
      "esqueci minha senha" chegou na caixa, sem cair no spam.

- **Critério de pronto:** dá pra colocar dado real sem medo de perder, sem ficar cego a
  erros, com auth definida e sem os gaps de segurança conhecidos.

- ✅ **FASE 1 CONCLUÍDA (2026-07-18).** Produção no ar em `https://domusigreja.com.br`
  (Hetzner VPS + Cloudflare Tunnel + Neon Frankfurt). Ver detalhes de deploy e os
  "gotchas" na memória do projeto (`producao-no-ar-deploy`).

---

### Fase 2 — Funcionalidades de valor pra igreja

> **Objetivo:** o que faz a igreja realmente querer usar.

- **Upload de foto** (membro e evento)
    - *Back:* armazenamento externo (S3 / Cloudflare R2 / similar — **evitar guardar
      binário no Postgres**), validação de tipo/tamanho, geração de URL. O campo `foto` já
      existe nas tabelas `membro` e `evento`.
    - *Front:* componente de upload com preview (recorte opcional).

- **Endereço estruturado** *(colunas, não tabela nova)*
    - *Decisão:* substituir `endereco VARCHAR(500)` por colunas na própria tabela `membro`:
      `cep`, `logradouro`, `numero`, `complemento`, `bairro`, `cidade`, `uf`. Isso
      **habilita filtro por bairro/cidade** sem JOIN extra.
    - *Back:* migration Flyway (nova estrutura + migração dos dados existentes), ajuste de
      DTOs.
    - *Front:* formulário com **auto-preenchimento via ViaCEP** (gratuito) ao digitar o CEP.

- [x] **Inscrição de membro em evento + preço e vagas** — **FEITO (2026-07-21):**
  inscrição dois níveis (self + inscrever outros), acompanhantes para visitantes,
  contagem de vagas com lock pessimista, `requer_inscricao` como toggle master, campo
  `batizado`, lista reduzida para membros (sem telefone de convidado) e completa para
  admins/líderes, preço apenas informativo (cobrança decidida na Fase 6).

- **Auditoria de evento (criado_por / atualizado_por)**
    - *Reutilizar o padrão que já existe em `movimentacao_financeira`* — barato e deixa o
      sistema consistente.
    - *Back:* colunas `criado_por_usuario_id` e `atualizado_por_usuario_id` no evento.
    - *Front:* exibir "criado por / atualizado por" na tela do evento.

- **Convite de acesso por e-mail (novo fluxo de provisionamento)**
    - *Motivação:* hoje o "conceder acesso" (`UsuarioService.concederAcesso`) exige que o
      **admin defina a senha** do membro e escolha a role de uma vez. Novo fluxo desejado:
      o admin **só escolhe a role e convida por e-mail**; o **próprio usuário define a sua
      senha** depois (reusando o fluxo de reset/definir senha da Fase 1).
    - *Back:* ao convidar, criar o `usuario` com role e **sem senha** (`senha_hash = null` —
      já habilitado pela migration do Google OAuth) e disparar e-mail de convite com link de
      definição de senha; o usuário convidado também pode entrar direto com Google (o e-mail
      é a chave). Depende do e-mail transacional (Fase 1).
    - *Front:* trocar o formulário de senha do "conceder acesso" por seleção de role + envio
      de convite.

- **Validação de formato de e-mail e telefone (BR)**
    - *E-mail:* validar **formato** no cadastro de membro (Zod no front + defensivo no
      back). Importante porque o e-mail vira a **chave de login** (ver Fase 1). Sem
      verificação de posse — o primeiro login com Google já cobre isso.
    - *Telefone:* só formato brasileiro (grátis), **sem SMS**.

---

### Fase 3 — Gestão de conta e configurações

- **Aba de Configuração:** perfil do usuário + dados da igreja (visualizar e editar).
- **Excluir conta.**
- **Lista de arquivados por módulo + exclusão definitiva** (usuários, membros, eventos…).
  Complementa o soft delete já existente; a exclusão definitiva atende ao **direito de
  eliminação da LGPD**.
- **Termos de Uso + Política de Privacidade.** Necessário sob a LGPD antes de usuário
  real; obrigatório antes de vender.

---

### Fase 4 — Dashboard / início

- Dashboard **simples de propósito**: 3–4 números-chave + 1 lista (ex.: próximos
  eventos). Nada de gráfico complexo por enquanto — dashboard é um buraco negro de tempo.

> **➜ A igreja entra no ar em algum ponto entre a Fase 3 e a Fase 4.**
> As fases seguintes são a camada "vender pra igrejas externas".

---

### Fase 5 — Camada comercial (self-service pra igrejas externas)

> **Objetivo:** abrir o cadastro para igrejas de fora se registrarem sozinhas.

- Como o **cadastro do dono via Google já foi construído na Fase 1**, aqui sobra:
    - **Expor o cadastro publicamente** (hoje é uso interno/piloto).
    - **Polir o onboarding pós-cadastro:** boas-vindas e próximos passos (continuar
      cadastro, cadastrar membro, ir pro painel…).
    - **Aviso de acesso a novos usuários:** quando o admin concede acesso a um membro,
      notificar por e-mail ("você tem acesso, entre com Google") — depende do e-mail
      transacional da Fase 1.
    - Qualquer trava comercial (ex.: escolha de plano) — depende do estudo da Fase 6.

---

### Fase 6 — Estudo (não é build)

- **Estudo de pagamento.** Não é construir do zero — é **decidir provedor e entender o
  modelo**. Pesquisar Stripe / Mercado Pago / Asaas / Pagar.me: taxas, se há custo
  fixo/mensalidade, tier gratuito, e como funciona para dois casos distintos:
  (a) cobrança de **eventos pagos** e (b) cobrança das **igrejas pelos planos do Domus**.
  *Saída do estudo:* uma recomendação de provedor + modelo, **não** código.

---

## Fora do escopo desta versão (anotado pra não esquecer)

Deixado para o **fim deste scope** ("versão pra minha igreja") ou depois:

- Filtros extras em movimentação financeira (ex.: por atribuinte/pessoa).
- Múltiplos atribuintes numa mesma movimentação financeira.
- Verificação de **posse** de telefone via SMS (pago, com atrito — só se houver
  necessidade real de antifraude).
- Expansão de campos de membro dirigida por uso real (YAGNI).
- Novos itens que surgirem — anotar aqui, em vez de embutir no meio do caminho.

---

## Decisões já tomadas (guardrails)

- Igreja é **design partner** (piloto/soft opening), não cliente comercial — sem
  self-service nem billing para o piloto.
- **Autenticação = híbrida (Google OAuth + e-mail/senha nativo).** Decisão confirmada em
  2026-07-14: as duas formas convivem. O e-mail/senha nativo (bcrypt) já existe e
  funciona; falta o "esqueci minha senha" (depende do e-mail transacional). O Google
  (login + cadastro) entra novo. Como o nativo continua, reset de senha, rate limiting e
  proteção a força bruta continuam valendo — não somem.
- **Provisionamento ≠ autenticação.** O admin concede acesso (provisionamento, sem
  OAuth); a pessoa loga com Google (autenticação). O **e-mail** (`membro.email`, único) é
  a chave que liga a identidade do Google ao usuário. O **primeiro login com Google** já
  serve de verificação de posse do e-mail.
- **Auth é fundação:** construída (Fase 1) antes de provisionamento de membros e
  configurações.
- Nos dois caminhos (Google ou nativo), **o app emite os próprios JWT + refresh** após
  identificar a pessoa; refresh/revogação e rate limiting valem para ambos.
- Endereço = **colunas estruturadas na tabela `membro`**, não tabela separada (habilita
  filtro por bairro sem over-engineering). Regra geral: tabela nova é para N-para-N ou
  dado repetido/compartilhado — não para 1-para-1.
- Telefone e e-mail = **validação de formato** apenas; SMS de posse fica fora por ora.
- Pagamento = **integrar provedor existente**, nunca construir do zero; e agora é só
  **estudo**.
- Auditoria de evento = **reusar o padrão de `movimentacao_financeira`**.

---

## Ordem de execução resumida

`Fase 1 (fundações + auth) → Fase 2 → Fase 3 → Fase 4` *(igreja no ar)* `→ Fase 5 → Fase 6`