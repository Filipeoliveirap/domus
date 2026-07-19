# Backlog — Dívida técnica & itens de próximo scope

> Lugar único para registrar **dívidas técnicas conscientes** e **features/itens deixados
> para depois** (fora do scope do piloto "minha igreja"). Sempre que uma decisão adiar algo,
> anotar aqui — para não se perder e resgatar quando abrir o próximo scope.
>
> Isto **não** substitui o roadmap (`CLAUDE.md`): o roadmap é o que vamos fazer agora, nas
> fases do piloto. Este arquivo é o que ficou **de fora** ou **para depois**.

---

## Dívida técnica (adiada de propósito, YAGNI/tempo)

- **Cache de `Usuario` no `SecurityFilter`.** Hoje o filtro de segurança bate no Postgres a
  cada requisição para carregar o usuário do JWT. Adiado por YAGNI (volume do piloto é baixo).
  Quando o tráfego crescer, cachear o usuário (ex.: Redis com TTL curto + invalidação em
  logout/alteração de role). Ver memória `cache-usuario-security-filter-adiado`.

- **Infra de teste de banco (Testcontainers).** O projeto não tem H2 nem Testcontainers; os
  testes de repositório rodam contra o Neon de testes (via `@AutoConfigureTestDatabase(replace=NONE)`)
  e exigem exportar as envs do `.env` no terminal. Ideal: subir um Postgres real em Docker por
  teste (fidelidade + isolamento), sem depender do Neon nem de env manual.

- **Maven wrapper quebrado.** Falta `.mvn/wrapper/maven-wrapper.properties`, então `./mvnw`
  não roda — usamos o `mvn` do sistema. Regerar o wrapper (`mvn wrapper:wrapper`).

- **CSP baseada em nonce no front (hardening).** A CSP atual do Next libera `'unsafe-inline'`
  e `'unsafe-eval'` (concessão ao Next.js sem nonce). Hardening real = CSP com nonce por
  requisição (via middleware), removendo os `unsafe-*`. Tarefa própria, considerável.

- ~~**Rate limiting: migrar bloqueio de login para Redis.**~~ **FEITO** (2026-07-16): o
  `LoginAttemptService` agora usa Redis (`login:attempt:*`/`login:block:*`). Junto entrou o
  rate limiting geral por IP (`RateLimitFilter`, janela fixa, global 100/min + auth 10/min).
  O que ficou **de fora** e pode virar dívida no futuro:
    - **Algoritmo sliding window / token bucket.** A janela fixa tem efeito de borda (pode-se
      mandar ~2x o limite na virada do minuto). Irrelevante pro piloto; trocar por Bucket4j se
      o volume exigir (o filtro é o único ponto de troca).
    - **Limite por usuário autenticado.** Hoje é só por IP. Um limite por `usuario_id` daria
      granularidade extra (ex.: um usuário abusando de dentro de uma rede compartilhada/NAT).
    - **Limites por rota individual.** Hoje há só dois tiers (global e auth). Se algum endpoint
      específico precisar de teto próprio, generalizar a configuração.

- **`X-Forwarded-For`: pegamos o PRIMEIRO elemento da lista.** `RateLimitFilter.resolverIp()`
  faz `forwarded.split(",")[0]`. Isso só é correto se o proxy **substituir** o header
  (`proxy_set_header X-Forwarded-For $remote_addr`). Se ele **acrescentar** (o mais comum:
  `$proxy_add_x_forwarded_for`), a lista fica `<forjado pelo cliente>, <ip real>` e o
  primeiro elemento é o **forjado** — um atacante escaparia do rate limiting só mandando o
  header. Hoje é inofensivo (`trust-forwarded-for=false`), mas vira crítico no dia em que
  for ligado. Decidir junto com a hospedagem: ou configurar o proxy para substituir, ou
  trocar o código para pegar o **último** elemento (o mais próximo do proxy confiável).

- **Rate limiting não conta requisições barradas pelo CSRF.** Descoberto na revisão da
  migração de cookie (2026-07-16): o `CsrfFilter` do Spring roda em ~order 1300 e o nosso
  `RateLimitFilter` em ~1898, então um flood de POST sem `X-XSRF-TOKEN` leva 403 e **nunca
  incrementa** `rl:global:<ip>`. As respostas são baratas (403 seco, sem tocar no banco),
  por isso ficou assim. Se virar vetor de abuso, mover o `RateLimitFilter` para antes do
  `CsrfFilter`.

- **HSTS do backend depende de `FORWARD_HEADERS_STRATEGY=framework` em prod.** O Spring só vê
  o salto HTTP interno do proxy, então `request.isSecure()` é false e o `HstsHeaderWriter`
  não escreve nada. A property foi adicionada (default `none`, como o `trust-forwarded-for`),
  mas **precisa virar `framework` em produção** — senão o bloco de HSTS do `SecurityConfig`
  é letra morta. Quem protege de fato é o HSTS do front (`next.config.ts`), que cobre a
  origem inteira; o do back é defesa em profundidade.

- **Backup: janela de perda de 24h e restauração manual.** O backup roda 1×/dia, então o
  pior caso é perder um dia de lançamentos. Aceito: a igreja lança dízimo no domingo e
  cadastra membro na quarta; redigitar isso é barato perto de dobrar as peças. Também **não
  há automação de restore** — restaurar é manual **de propósito**: restauração automática é
  como se apaga produção por engano. Se o volume crescer, avaliar 2×/dia.

- **Backup depende do GitHub Actions seguir habilitado.** Workflows agendados são
  desativados após **60 dias sem commit** no repositório. Mitigado pelo Sentry Crons (avisa
  em ~24h), não eliminado. Se o projeto hibernar, reativar na mão.

- **O agendamento só funciona na branch PADRÃO.** Descoberto em 2026-07-17: o workflow
  vivia só na `producao` e **nunca teria rodado** — o GitHub só executa `schedule` na branch
  default (`main`). Foi o que motivou o merge da Fase 1 para a `main` (PR #18). Lembrar
  disso ao criar qualquer workflow agendado novo.

- **Armadilha do principal desanexado (documentar para não repetir).** Descoberto por um bug
  real no `/auth/me` (2026-07-16, corrigido): o `Usuario` que chega em
  `@AuthenticationPrincipal` / `UsuarioAutenticado.get()` é uma entidade **desanexada**. O
  `SecurityFilter` é um *servlet filter* e roda **antes** do open-in-view (que é um
  *interceptor de MVC*), então o `EntityManager` do `findById()` dele já fechou quando o
  controller executa. Consequência prática:
    - ler `usuario.getIgreja().getId()` **funciona** (o proxy já sabe o id);
    - ler `usuario.getIgreja().getNome()` **lança LazyInitializationException** (`igreja` é LAZY);
    - `membro` (EAGER) e `role` (`@ManyToOne` sem fetch = EAGER) são seguros.
  Varredura feita em 2026-07-16: `UsuarioAutenticado` só expõe `getIgrejaId`/`getUsuarioId`/
  `getRole` (todos seguros) e `get()` cru nunca é chamado de fora. **Regra:** de dados do
  principal, use só o **id**; qualquer outro campo, consulte. Ver `findSessaoById`.
  *Nenhum teste com Mockito pega isso* — a entidade é montada na mão e lazy não existe.

- **O outbox só é seguro por causa do `@Transactional` (frágil por construção).**
  `MovimentacaoDocument.de()` lê `getCategoria().getNome()` e `getMembro().getNome()` — os
  dois **LAZY**. Isso roda em `OutboxProcessador.processar()`, que é `@Scheduled` **e**
  `@Transactional`, e é só a transação que mantém a sessão aberta (num job agendado não há
  open-in-view). Remover esse `@Transactional`, ou chamar `SincronizadorEntidade.indexar()`
  de um contexto sem transação (ex.: um `@Async` futuro), reintroduz a
  LazyInitializationException na hora. Verificado em 2026-07-16: é o único `@Scheduled` do
  projeto, não há `@Async` nem event listener, e as reindexações são todas `@Transactional`.

- **Coleções `@OneToMany` da `Igreja` não são lidas por ninguém.** `usuarios`, `membros`,
  `eventos`, `categorias` e `movimentacoes` (`Igreja.java:54-66`) estão mapeadas mas nenhum
  código as acessa (verificado em 2026-07-16). São mapeamento morto e uma arma engatilhada:
  o dia em que alguém serializar uma `Igreja` ou tocar nelas fora de sessão, carrega a
  igreja inteira ou estoura. Avaliar remover (YAGNI) — nada as usa hoje.

- **Aviso do Mockito (self-attaching agent).** Testes logam warning de que o Mockito se
  auto-anexa como agente; em JDKs futuros deixará de funcionar. Configurar o byte-buddy/mockito
  como Java agent no surefire.

---

## Segurança / autorização — a discutir (decisão de produto)

- **Login CSRF (resíduo aceito na migração de cookie, 2026-07-16).** As rotas públicas de
  auth (`/auth/login`, `/auth/google/*`, `/igrejas/registrar`, `/auth/forgot-password`,
  `/auth/reset-password`) são isentas do double-submit: rodam sem sessão para um atacante
  cavalgar, e protegê-las exigiria buscar um token CSRF antes de cada formulário público em
  4 telas. Fica possível o **login CSRF** (forçar a vítima a logar na conta do atacante e
  digitar dados achando que é a própria). Impacto modesto e o `SameSite=Lax` já o barra na
  prática (é POST cross-site). Reavaliar se surgir fluxo sensível pré-login.

- **Janela de convivência cookie+header.** A migração para cookie httpOnly matou toda sessão
  existente (o `SecurityFilter` parou de ler o header `Authorization`). Foi aceitável porque
  não havia usuário real. Se um dia for preciso migrar auth sem deslogar todo mundo, o
  padrão é ler cookie **e** header por uma janela e só então remover o header.

- **`CORS_ALLOWED_ORIGINS` virou item crítico de produção (2026-07-16).** Com a sessão em
  cookie, `allowCredentials(true)` + uma origem liberada = chamadas **plenamente
  autenticadas** feitas por aquela origem, porque o navegador anexa o `domus_access` sozinho.
  Antes, com o token no header, uma origem liberada não conseguia nada sem já ter o token.
  O valor atual (`http://localhost:3000`) está correto; o ponto é que essa env deixou de ser
  conveniência e virou interruptor de comprometimento de sessão. Conferir com cuidado ao
  configurar produção.

- **Acesso horizontal a dados de membro dentro da mesma igreja (a decidir a intenção).**
  Hoje `GET /membros/**` permite o perfil `MEMBRO`, e `buscarPorId` escopa **só por igreja**
  (`findByIdAndIgrejaId`), **sem checagem de dono**. Consequência: qualquer `MEMBRO` da igreja
  vê os dados completos de qualquer outro membro (`email`, `telefone`, `endereco`,
  `dataNascimento`, `observacoes`, etc.) — seja por `GET /membros/{id}` ou pela listagem
  `GET /membros`. **Não é falha de sigilo de id** (o id não é segredo; a autorização é que
  decide) — é uma decisão de controle de acesso.
  - *Perguntar:* isso é intencional (lista de contatos aberta a toda a igreja) ou os campos
    sensíveis (endereço, telefone, `observacoes` — possíveis notas pastorais privadas) deveriam
    ser restritos a ADMIN/LÍDER?
  - *Opções se for restringir:* (a) tirar `MEMBRO` do `GET /membros`; (b) filtrar campos por
    perfil (membro vê perfil reduzido); (c) membro só vê o próprio registro.
  - *Escopo maior:* fazer uma **revisão de autorização por perfil em todos os módulos** (quem vê
    o quê) — não só membros. Descoberto em 2026-07-16 discutindo o risco de id em localStorage.

---

## Frontend — robustez de sessão (a vigiar)

- **Logout indevido ao falhar refresh (a vigiar).** Descoberto em 2026-07-16: um MEMBRO abrindo
  `/financeiro/movimentacoes` era deslogado. Causa: a página disparava uma query de admin
  **sem gate de permissão** (`useCategoriasSelect()`), que tomava 401 (token expirado) e o refresh
  falhava → `encerrarSessao()`. **Corrigido** gateando a query por `autorizado` e adicionando o
  `AuthGuard` no layout `(app)`. *Pulga que fica:* se um 401 + refresh problemático desloga, em
  tese pode atingir um usuário autorizado num momento ruim (ex.: corrida na rotação/detecção de
  reuso do refresh). Sem repro por ora — observar; se reaparecer, instrumentar o interceptor do
  axios (`src/lib/api.ts`) e o fluxo de rotação.
- **403 de CSRF não tem caminho de recuperação no front (a vigiar).** O interceptor do
  `api.ts` só reage a 401. Se o cookie `XSRF-TOKEN` faltar quando um POST dispara, o Spring
  devolve 403 e o usuário vê um erro genérico sem saída além de recarregar. Hoje isso não
  deve acontecer: o `setCsrfRequestAttributeName(null)` força a resolução ansiosa do token,
  então **toda** resposta traz `Set-Cookie: XSRF-TOKEN` — inclusive o 401 do `/auth/me`, que
  sempre precede qualquer POST. Ou seja, funciona por causa da ORDEM dos eventos, não por
  uma defesa explícita. Se aparecer 403 inexplicado, tratar o código de erro de CSRF
  refazendo a busca do token.

- **Padrão a varrer:** garantir que nenhuma página acessível a papéis sem permissão dispare
  queries de admin (gate por `enabled: autorizado`). Só a de movimentações tinha o problema, mas
  vale uma passada nas demais quando mexer nelas.

## Observabilidade — fora do escopo da entrega de Sentry (2026-07-16)

O Sentry (back + front) e os logs estruturados foram feitos. Ficou para depois:
- **Upload de source maps + release tracking** (front): precisa de `SENTRY_AUTH_TOKEN` no
  build de CI/prod pra o stack trace do Sentry apontar pro código original (não o minificado).
  `withSentryConfig` já está pronto pra isso; falta o token + pipeline.
- **APM / performance tracing** (`tracesSampleRate` está 0 nos dois lados) — só se houver
  necessidade real de medir latência; consome cota do tier grátis.
- **Envio de logs pra um agregador central** (Loki/ELK/CloudWatch). Hoje os logs JSON saem no
  stdout; em prod alguém precisa coletá-los. Depende de onde a app for hospedada.
- **Afinação de regras de alerta** no painel do Sentry (quais erros notificam, para quem).

## Fora do scope do piloto (próximo scope / camada comercial)

- **Integração com Google Calendar (agendar eventos).** Módulo **separado** de autorização de
  API — Authorization Code flow com `access_type=offline` + escopo `calendar`, guardando o
  refresh token do Google **por usuário**, com botão explícito "Conectar agenda". NÃO se mistura
  com o login (que é só identidade). Ver memória `google-oauth-auth`.

- **Fluxo de convite por e-mail (novo provisionamento).** Hoje o admin, ao "conceder acesso",
  define a senha do membro. Desejado: admin só escolhe a role e **convida por e-mail**; o próprio
  usuário define a senha (reusa o reset). Já movido para a **Fase 2** do roadmap (o Google OAuth
  já deixou `senha_hash` nullable, então o back está pronto para usuários sem senha).

- Itens já listados em "Fora do escopo desta versão" no `CLAUDE.md` (filtros extras em
  financeiro, múltiplos atribuintes, verificação de posse de telefone via SMS, expansão de
  campos de membro por uso real) — mantidos lá; referência cruzada aqui.

---

## Warnings conhecidas e benignas (não são bug)

- **Botão do Google Sign-In em dev:** o console loga `[GSI_LOGGER]: The given origin is not
  allowed for the given client ID` (403 no render do botão) e `Cross-Origin-Opener-Policy would
  block the window.postMessage call`. Ambas aparecem em Chrome e Firefox, mas **login e cadastro
  funcionam** (o sign-in real usa popup). São do lado do Google (script `client:380`), não da
  nossa CSP/COOP (não setamos COOP). Verificado em 2026-07-15. Em prod (HTTPS + domínio real)
  tende a sumir. Se incomodar em dev: conferir a origem em "Authorized JavaScript origins" e
  aguardar propagação.

## Bugs conhecidos (corrigir na fase apropriada)

- ~~**`Sidebar.tsx` referencia `state.foto`** que não existe em `AuthState`.~~ **RESOLVIDO**
  (2026-07-16): adicionado `foto: string | null` ao `authStore` (fica `null` até a feature de
  upload de foto da Fase 2 popular o campo). O `next build` de produção volta a passar.

---

## Igrejas vinculadas (V12/V13) — adiado de propósito

Decidido durante a implementação da feature (2026-07-19). Nada aqui é esquecimento:

- **Histórico de vínculo/desvínculo** (`desvinculado_em` + tabela de histórico). Hoje sair da
  família **apaga** `vinculado_em`/`vinculado_por`. Se um dia for preciso auditar "quem entrou e
  saiu quando", vira tabela de histórico. YAGNI agora: ninguém pediu.
- **Status "Ativo/Pendente" na congregação.** Estava no protótipo, foi **removido**: não existe
  estado pendente porque o design descartou solicitação+aprovação. Só volta se/quando entrar o
  fluxo de convite por e-mail — aí o "Pendente" passa a significar algo.
- **`fuso_horario` na igreja.** Tentador (o Brasil tem 4), mas a coluna sozinha é ilusão de
  suporte a fuso: só serve se o sistema inteiro formatar data por igreja. **Armadilha conhecida
  do módulo de eventos** — reavaliar se aparecer igreja fora de BRT.
- **`Endereco` (`@Embeddable`) mora em `modules.membro`** e é reusado por `Igreja`. Funciona e é
  DRY, mas o lugar certo seria `shared`. Mover quando alguém encostar no módulo de membro.
- **Listas navegáveis** (a mãe folhear membros/eventos das filhas) e **irmãs verem eventos umas
  das outras** — continuam fora, como o spec já dizia. Depende de consultar o pastor primeiro.
- **Upload do logo da igreja.** A coluna `logo_url` existe desde a V13, mas a tela só a preserva
  (não faz upload). Entra junto com o upload de foto de membro/evento da Fase 2.
- ~~**Confirmações usam `window.confirm`.**~~ **RESOLVIDO** (2026-07-19): criado
  `components/common/ModalConfirmacaoCritica`, no modelo "digite o nome para confirmar" do
  GitHub — lista o que se perde e o que se mantém, e exige digitar o nome da igreja. Usado em
  desvincular (sede) e sair da família (congregação). **Reutilizável**: é o componente a usar em
  toda ação destrutiva daqui pra frente (ex.: excluir conta e exclusão definitiva da Fase 3).

### Auditoria de segurança da feature (2026-07-19) — resíduos

Dois agentes revisaram back e front. 9 dos 11 achados foram corrigidos na hora. Ficaram:

- **Focus trap no `ModalConfirmacaoCritica`.** `Esc` e clique fora fecham, mas `Tab` escapa do
  diálogo para o conteúdo de fundo. Acessibilidade, não segurança.
- **Semântica ARIA das abas em `/financeiro/relatorios`.** Lá são abas de verdade e faltam
  `aria-controls` + `role="tabpanel"` + navegação por setas. (Em `/configuracoes` já foi
  corrigido: eram links de navegação e viraram `aria-current="page"`.)
- **`CascadeType.ALL` + `orphanRemoval` nas 5 coleções de `Igreja`.** Não há bug hoje (o código
  novo nunca inicializa as coleções), mas é bomba armada: um `builder()` + `save()` sobre id
  existente apagaria membros/usuários/eventos/movimentações. Remover a cascata de escrita da
  raiz de tenant exige teste de integração — não fazer no susto.
- **Trigger e lock cobrem a regra dos 2 níveis, mas por caminhos diferentes.** O lock
  (`VinculoService`) resolve a corrida; o trigger (V14) resolve caminhos futuros que não passem
  pelo serviço. Um NÃO substitui o outro: trigger com `SELECT` simples não vê transação
  concorrente em READ COMMITTED — por isso ele usa `FOR UPDATE`.
