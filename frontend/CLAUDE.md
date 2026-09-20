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

- - **Modelo de autenticação: híbrido (Google OAuth + e-mail/senha nativo)**
- Decisão: duas formas de entrar, que convivem — "Entrar com Google" (OAuth) e
  e-mail/senha nativo (que JÁ existe e funciona, com proteções tipo bcrypt).
- Falta no nativo: função "esqueci minha senha" (reset via link por e-mail) — depende
  do e-mail transacional.
- Entra de novo: login E cadastro com Google. No cadastro, o Google cria igreja +
  primeiro membro + primeiro usuário (ADMIN_IGREJA) já com e-mail e nome verificados.
- Provisionamento (admin dá acesso) ≠ login. Depois de provisionado, o membro entra por:
  (a) Google — vínculo pelo e-mail (membro.email é único), primeiro login verifica posse;
  (b) Nativo — precisa definir uma senha antes, reusando o MESMO fluxo do reset.
- Sessão: nos dois caminhos, após identificar a pessoa (token Google ou bcrypt), o
  backend emite os próprios JWT + refresh. Logo, refresh/revogação e rate limiting valem
  para ambos.

- **E-mail transacional** (Resend / SendGrid / AWS SES)
    - *Motivação:* reset de senha, confirmação de inscrição em evento e aviso de acesso concedido a novos
      usuários dependem disso. *(Reset de senha some com o Google-only.)*
    - *Back:* serviço de envio abstraído (interface + implementação do provedor), templates,
      retry simples em caso de falha.
    - *Front:* estados de "e-mail enviado" quando aplicável.

- **Backup automático do Postgres** *(entra como item de segurança)*
    - *Motivação:* dado financeiro e cadastral real; perda é imperdoável.
    - *Back/infra:* rotina agendada de dump, política de retenção e **teste periódico de
      restauração** — backup que nunca foi restaurado não é backup, é esperança.

- **Rastreamento de erro (Sentry) + logs estruturados**
    - *Back:* integração do Sentry; log estruturado por request (com `igreja_id`/usuário
      quando aplicável, **sem vazar dado sensível**).
    - *Front:* Sentry no Next.js para erros de cliente.

- **Correções de segurança (as "nuances")**
    - **Refresh token + revogação:** hoje o JWT *stateless* não revoga — o "logout" não
      desloga de fato. Introduzir refresh token + lista de revogação (ex.: Redis) e rotação.
      *(Continua valendo com Google-only: os tokens são emitidos pela aplicação.)*
    - **Rate limiting em todos os endpoints** (não só no login).
    - **Segredos em variáveis de ambiente** (nunca no código/repositório).
    - **CORS restrito + security headers.**
    - Revisão de **validação de input** em toda entrada.

- **Critério de pronto:** dá pra colocar dado real sem medo de perder, sem ficar cego a
  erros, com auth definida e sem os gaps de segurança conhecidos.

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

- **Inscrição de membro em evento + preço e vagas**
    - *Back:* nova tabela de inscrição (ex.: `inscricao_evento`: `evento_id`, `membro_id`,
      `status`, timestamps). Aqui a tabela nova **faz sentido** — é uma relação N-para-N
      legítima (contraste proposital com o endereço, que era 1-para-1). Adicionar `preco` e
      `vagas` (capacidade) no evento, com regra de negócio que impede passar do limite.
    - *Front:* fluxo de inscrição, contador de vagas, estado "esgotado".

- **Auditoria de evento (criado_por / atualizado_por)**
    - *Reutilizar o padrão que já existe em `movimentacao_financeira`* — barato e deixa o
      sistema consistente.
    - *Back:* colunas `criado_por_usuario_id` e `atualizado_por_usuario_id` no evento.
    - *Front:* exibir "criado por / atualizado por" na tela do evento.

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
- **Autenticação = Google-only (OAuth).** Elimina senha, reset, verificação de e-mail e
  força bruta no login. *(Reserva e-mail/senha em aberto, a confirmar.)*
- **Provisionamento ≠ autenticação.** O admin concede acesso (provisionamento, sem
  OAuth); a pessoa loga com Google (autenticação). O **e-mail** (`membro.email`, único) é
  a chave que liga a identidade do Google ao usuário. O **primeiro login com Google** já
  serve de verificação de posse do e-mail.
- **Auth é fundação:** construída (Fase 1) antes de provisionamento de membros e
  configurações.
- Google-only **não invalida a segurança da Fase 1**: o app segue emitindo JWT + refresh
  após o Google atestar; refresh/revogação e rate limiting continuam valendo.
- Endereço = **colunas estruturadas na tabela `membro`**, não tabela separada (habilita
  filtro por bairro sem over-engineering). Regra geral: tabela nova é para N-para-N ou
  dado repetido/compartilhado — não para 1-para-1.
- Telefone e e-mail = **validação de formato** apenas; SMS de posse fica fora por ora.
- Pagamento = **integrar provedor existente**, nunca construir do zero; e agora é só
  **estudo**.
- Auditoria de evento = **reusar o padrão de `movimentacao_financeira`**.

---

## Convenções de teste do front

A infra de teste mora na raiz do `frontend/` e divide em **3 camadas**:

| Camada       | Ferramenta                          | Quando usar                              |
|--------------|-------------------------------------|------------------------------------------|
| Unit         | Vitest, sem DOM                     | Função pura, hook com lógica, schema Zod |
| Component    | Vitest + jsdom + RTL + MSW          | Render, evento, formulário              |
| E2E          | Playwright (chromium + webkit)      | Fluxo real no navegador                  |

**Caminhos:**
- Unit: `src/lib/<dominio>/__tests__/*.spec.ts`
- Component: `src/components/<componente>/__tests__/*.test.tsx` ou
  `src/app/<rota>/__tests__/*.test.tsx`
- E2E: `e2e/*.spec.ts`

**Comandos:**
- `npm run test` roda tudo (unit + component). Em watch: `npm run test:watch`.
- `npm run test:unit` só unit (milissegundos).
- `npm run test:component` só component (jsdom).
- `npm run test:e2e` sobe Playwright (abre `next dev` se não houver `PLAYWRIGHT_BASE_URL`).

**Nomes de teste em snake_case PT.** Mesmo padrão do back. Exemplo:
```ts
describe("PessoaCard", () => {
  it("mostra_nome_principal_quando_apelido_esta_vazio", () => { ... });
});
```

**Helpers:**
- `renderizarComQuery(<Componente />)` em vez de `render` quando o componente consome
  TanStack Query (a maioria consome). O helper embrulha em `QueryClientProvider` com
  `retry: false` e `gcTime: 0`. Importar de `@/test/test-utils`.
- `server` (MSW) pra mockar `/api/*` em teste de componente. Sem handlers globais:
  cada teste adiciona os seus com `server.use(http.get(...))`. Importar de
  `@/test/setup-msw`.

**Cobertura:** v8 provider, gera `coverage/`. Não falha por cobertura baixa — quem
decide é o code-reviewer. Use pra ver o que escapou, não como gate.

**Browser e2e:** dois projetos obrigatórios:
- `chromium` (Desktop Chrome).
- `webkit` (iPhone 14). Não pula. O Domus é mobile-first e a animação de saída de
  modal depende de `@starting-style`, que Safari < 17.4 não suporta — por isso
  `useFecharAnimado`+`.saindo` existe. Sem teste em webkit, isso quebra silencioso
  em iPhone real.

---

## Agent skills

Pra tarefas no front, considerar (além dos globais de `~/.claude/agents/`):
- `dev-front` *(planejado, ainda não criado)* — especialista Next/React/TanStack/RHF
  do Domus. Cobre o que `dev-back` cobre pro back: convenções, arquitetura, antipadrões
  deste repo. Mesmo papel do `dev-back` (ver `backend/api/.claude/agents/dev-back.md`).
- `test-writer` *(planejado)* — escreve os 3 tipos de teste seguindo as convenções
  acima. Ganha contexto via `dev-front` quando o código a testar já existe.

A skill `test-driven-development` (global) dita quando escrever o teste antes;
este bloco dita COMO escrever.

---

## Ordem de execução resumida

`Fase 1 (fundações + auth) → Fase 2 → Fase 3 → Fase 4` *(igreja no ar)* `→ Fase 5 → Fase 6`