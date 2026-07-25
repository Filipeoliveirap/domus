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

---

## Módulo de eventos — ideias levantadas no brainstorm de inscrição (2026-07-20)

Surgiram enquanto desenhávamos a inscrição em evento. **Não entram na Spec A** (inscrição), mas
são o roteiro das specs seguintes. Registradas aqui para não se perderem.

### Decomposição acordada

O cadastro de evento virou quatro entregas independentes, nesta ordem:

| Spec | Conteúdo | Estado |
|------|----------|--------|
| **A** | Inscrição + vagas + preço informativo + acompanhantes + `batizado` | **feita** |
| **B** | Cadastro enriquecido: layout 2 colunas, responsável, tipo, locais cadastrados | **feita** (2026-07-22) |
| **C** | Recorrência (culto semanal cadastrado uma vez) | depois do piloto usar |
| **D** | Campos personalizados por evento | por último, se a igreja pedir |
| **E** | Programação do evento + equipe servindo | fora desta entrega, ver abaixo |

Motivo da ordem: A e B são independentes (dá para fazer B com a igreja já usando A). C e D só
ficam bons com uso real informando o desenho — construir antes é construir no escuro.

### Spec B — cadastro de evento enriquecido — **CONCLUÍDA (2026-07-22, migration V3)**

Entregue: `local_evento` (tabela, com capacidade que **sugere** vagas — não impõe limite),
`local_texto` como alternativa ad-hoc (XOR com `local_id` via CHECK), `tipo` (texto livre
com autocomplete/normalização, não confundido com "categoria"), `responsavel_pessoa_id`,
auditoria (`criado_por_usuario_id`/`atualizado_por_usuario_id`, padrão de
`movimentacao_financeira`), layout de duas colunas no front, e **elegibilidade por perfil**
(faixa etária, estado civil, sexo, batizado) avaliada no momento da inscrição — ver seção
abaixo. Banner reusou o `<UploadFoto>` já existente da Fase 2.

**Ficou de fora desta entrega** (ver itens específicos mais abaixo neste arquivo):
- Capacidade do local **impondo** limite de vagas (hoje só sugere; nada barra cadastrar
  vagas acima da capacidade).
- Lista de espera quando as vagas esgotam.
- Recorrência (Spec C), campos personalizados (Spec D) e programação/equipe (Spec E) —
  continuam como specs futuras, não tocadas por esta entrega.

- **Layout de duas colunas** (do protótipo): coluna esquerda = *o que é o evento* (título, data,
  local); coluna direita = *como é administrado* (responsável, banner, visibilidade). A
  compactação percebida vem daí — o campo raro para de atrapalhar o campo comum.
- **Locais como entidade cadastrada**, não texto livre: "Santuário Principal", "Salão Social",
  cada um com capacidade. Cadastra uma vez, reusa sempre; a capacidade **sugere** o número de
  vagas. Inclui localizações rápidas ad-hoc ("casa de fulano", "chácara tal").
- **Herdar endereço da igreja**: ao escolher o local do templo, o detalhe do evento mostra o
  endereço já cadastrado em `igreja` — sem redigitar em cada evento.
- **Responsável/organizador**: membro escolhido por busca na lista de membros.
- **Tipo do evento** — ⚠️ **não chamar de "categoria"**: o nome já significa outra coisa no Domus
  (`categoria_financeira`) e a ambiguidade contaminaria toda conversa futura.
- **Banner do evento**: depende do upload de foto da Fase 2 (mesma pendência do logo da igreja).

### Spec C — recorrência

Cadastrar o culto uma vez em vez de toda semana. **É um projeto próprio, não um toggle.** A
pergunta que define o desenho: quando o pastor edita o culto de 15/08, ele mudou *aquele dia* ou
*todos os cultos*? E cancelar um feriado exige "exceções da série". Todo calendário maduro
(Google, Outlook) tem tela dedicada a isso. O protótipo mostrava um toggle sem nada embaixo —
tinha pulado a parte difícil.

### Spec D — campos personalizados por evento

Marcar no cadastro "este evento precisa de algum dado além do cadastro da pessoa?" e abrir um
formulário para o inscrito preencher. Na prática é **um criador de formulários** (tipo de campo,
obrigatoriedade, validação, exibição das respostas) — sozinho é maior que a inscrição inteira.

**Fica por último de propósito:** ainda não sabemos *quais* campos a igreja pede. A aposta é que
os primeiros casos sejam "tamanho da camiseta" e "vai de van?", e que um campo de observação
livre resolva os dois. Ver três eventos reais antes de construir o gerador.

### Elegibilidade por perfil — **CONCLUÍDA (2026-07-22, migration V3 + Specs A/B)**

Quatro regras **independentes**, cada uma avaliada no momento da inscrição (não no cadastro
do evento), com bloqueio explícito por código próprio quando o dado da pessoa falta em vez
de deixar passar em silêncio:

- **Faixa etária** (`idade_min`/`idade_max`, com `recorte_etario` como rótulo — Kids,
  Jovens, 3ª idade). Pessoa sem `data_nascimento` é **bloqueada** com código próprio, não
  deixada passar — decisão explícita para não virar suporte silencioso.
- **Estado civil** (`restricao_estado_civil`) — lido de `pessoa.estado_civil`.
- **Sexo** (`restricao_sexo`) — habilitado pela coluna nova `pessoa.sexo` (V3).
- **Só batizados** (`exclusivo_membros`, já existente desde a Spec A) — lido de
  `pessoa.vinculo = MEMBRO`.

Quem gerencia (ADMIN_IGREJA/LIDER) pode **contornar** a maioria das restrições ao inscrever
outra pessoa, mas `VAGAS_ESGOTADAS` **não é contornável** por ninguém — e a auto-inscrição
nunca contorna nada, nem para admin.

**Ficou de fora:** nada pendente da elegibilidade em si — os itens de fora são os já listados
acima na Spec B (capacidade impondo limite de vagas, lista de espera) e as Specs C/D/E.

### Selos e filtros por tipo de evento — **CONCLUÍDA (2026-07-22)**

Selo compacto no card do evento indicando o recorte ("Kids", "Jovens", "3ª idade"), e filtro
correspondente na listagem, usando `recorte_etario` como dado estruturado.

### Equipe servindo no evento (ideia nova)

Registrar quem **serve**, não quem assiste: "tia Ana com as crianças", "ministério de música da
igreja X", "pastor Y na pregação". É uma lista de pessoa/grupo + função, distinta da lista de
inscritos — quem serve não ocupa vaga.

Decidir na hora: função como texto livre (barato, sujeita a variação de escrita) ou lista
fechada de funções (consistente, exige manutenção). Provável começar com texto livre e observar
o que a igreja realmente digita.

---

## Falta harness de teste de autorização por endpoint (descoberto 2026-07-20)

Ao adicionar os matchers de `/eventos/*/inscricoes**` no `SecurityConfig`, a Task 2 original
previa um teste MockMvc batendo na `SecurityFilterChain` de verdade (`membroPodeSeInscreverEmEvento`
/ `membroNaoVeListaDeInscritos`). **Não existe esse harness no projeto.** `SecurityFilterTest`
é um teste unitário do `SecurityFilter` (o JWT filter) com Mockito puro — não sobe contexto
Spring, não passa pela `authorizeHttpRequests`, então não tem como pegar bug de **ordem** de
`requestMatchers`.

Consequência prática: erro de ordenação de matcher (curinga genérico casando antes da regra
específica) **compila limpo e passa em todos os testes unitários**, e só aparece testando ao
vivo (curl/Postman) contra o servidor rodando. Foi exatamente assim que a armadilha de
`/igrejas/*` foi descoberta antes, e é como esta de `/eventos/*/inscricoes` está sendo validada
agora — não há rede automatizada pegando isso hoje.

Ideal futuro: um `@SpringBootTest` com `@AutoConfigureMockMvc` (ou `WebMvcTest` importando o
`SecurityConfig`) que suba a `SecurityFilterChain` real e teste, por perfil, quais rotas dão
403/200 — pegaria esta classe inteira de bug no CI, não só na mão. Ficou de fora desta task de
propósito (harness é trabalho maior, tarefa própria) — anotado aqui para não se perder.

### Spec E — programação do evento + equipe servindo (2026-07-21)

Veio do protótipo "Evening Flow": uma linha do tempo do evento (19:00 café, 19:30 louvor,
20:15 pregação). Fundir com a **equipe servindo** já anotada acima — é a mesma estrutura vista de
dois ângulos: a linha diz *o quê*, *quando* e *quem*. "20:15 — Pregação — Pr. João".

Entidade nova (`programacao_evento`: evento_id, horario, titulo, responsável opcional). Fica
**depois da Spec B** — não atrasa a inscrição, e o desenho melhora com uso real.

Outros resíduos dos protótipos, para quando as telas correspondentes forem feitas:

- **Exportar lista de inscritos** (CSV/PDF). O protótipo tinha o botão; foi **removido** da
  entrega por não ter endpoint — botão que não faz nada é pior que botão ausente.
- **"What to Prepare"** (o que levar) do protótipo é caso da **Spec D** (campos personalizados),
  não campo próprio.
- **`adicionarAcompanhante` não tem `usuarioId` real no log** — usa `meuMembroId` como proxy.
  Estender a assinatura quando alguém encostar no método.

### Contribuintes: filtro, relatório e múltiplos por lançamento (2026-07-22)

Pedido do autor. **As duas partes são a mesma feature** e devem ser feitas juntas — fazer o
relatório primeiro e depois mudar a cardinalidade obrigaria a reescrever o relatório.

**1. Relatório e filtro por contribuinte.** Quem contribuiu, quanto, em que período. Combina
com o filtro por **vínculo** já existente (contribuições de membros × de congregantes), que é
justamente o recorte que a liderança pede.

**2. Múltiplos contribuintes por movimentação.** Hoje `movimentacao_financeira.pessoa_id` é
uma FK única — uma movimentação, um contribuinte. Precisa virar N-para-N (tabela de junção),
porque uma oferta pode vir de um casal ou de uma família.

⚠️ **A parte cara não é a tabela, é o que depende dela.** Ao passar de 1 para N:
- o **valor** precisa decidir se é rateado entre os contribuintes ou repetido para cada um —
  e a resposta muda toda soma de relatório. Decidir isso ANTES de escrever qualquer código.
- relatórios que hoje agrupam por `pessoa_id` passam a contar em dobro se ingênuos;
- a busca (`MovimentacaoDocument.pessoaNome`) indexa um nome só;
- o filtro por vínculo precisa definir o que fazer quando os contribuintes têm vínculos
  diferentes (um membro e um congregante na mesma oferta).

Estava no CLAUDE.md como "múltiplos atribuintes" fora de escopo desde o começo; o autor
confirmou em 2026-07-22 que quer.

---

## Upload de foto (V2, 2026-07-22) — resíduos

- **WebP como formato de entrada.** A spec previa aceitar JPEG, PNG e WebP; ficou só JPEG e
  PNG. Motivo: `ImageIO` do Java 21 não lê WebP sem uma dependência extra (ex.:
  `webp-imageio`), e na prática os seletores de arquivo do celular/navegador entregam JPEG
  ou PNG. Reavaliar se aparecer um caso real de upload em WebP.

- **Revisar `next/image` nas telas de foto.** Hoje elas usam `<img>` com
  `eslint-disable` justificado como "URL de storage externo" — justificativa que **deixou
  de valer**: as fotos são servidas pelo próprio domínio (`GET /fotos/{id}`), não por uma
  URL de R2. Trocar por `next/image` (otimização, lazy loading) é seguro agora, mas não
  entrou nesta entrega de propósito — misturaria dois assuntos (ver spec de upload de foto).

- **CDN de borda para `/fotos/{id}`.** Toda imagem passa pela própria API hoje; a resposta
  é `Cache-Control: immutable`, então cada navegador busca uma vez só — suficiente no
  tamanho de uma igreja. Se o volume um dia incomodar, a saída é colocar o Cloudflare na
  frente com cache de borda, sem mexer no modelo (o id nunca é reaproveitado, então cache
  de borda não tem problema de invalidação).

### Separar as credenciais de backup das de foto (2026-07-22)

Hoje **o mesmo token do R2** atende os dois buckets, com leitura e escrita em ambos.

⚠️ **Isso desfez uma proteção que existia por desenho.** O bucket de backup era **write-only**:
o CI escrevia e não conseguia ler. Se aquela credencial vazasse, o atacante gravaria lixo, mas
**não baixaria os backups** — que contêm o banco inteiro da igreja.

Foto precisa de leitura (a API busca os bytes para servir), então o token ganhou leitura — e o
backup deixou de ser write-only junto.

**O certo:**

| Uso | Bucket | Permissão |
|---|---|---|
| Backup | `domus-backups` | **só escrita** |
| Fotos | `domus-fotos` | leitura e escrita |

Dois tokens distintos. Quando separar, lembrar que o backup usa os secrets do **GitHub**
(`R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`) e as fotos usam o `.env.prod` da **VPS**
(`R2_FOTOS_*`) — são lugares diferentes, e trocar num não afeta o outro.

Origem: em 2026-07-22 as credenciais foram rotacionadas porque vazaram numa conversa, e na
recriação os dois usos ficaram com o mesmo token.

### ⚠️ Limpeza de foto de arquivada pode falhar por FK RESTRICT (a verificar) — 2026-07-22

`LimpezaFotosJob.limparDeArquivadas` chama `fotoService.remover`, que faz `DELETE FROM foto`.
Mas `pessoa.foto_id` é `ON DELETE RESTRICT` (V2) e a pessoa arquivada **ainda referencia** a
foto — o DELETE seria recusado pelo banco. O teste do job é Mockito e não exercita a FK, então
não pega. Falta desvincular (`pessoa.foto_id = NULL`) antes de apagar a foto. Reproduzir contra
Postgres real (arquivar pessoa com foto, avançar o corte, rodar o job) e corrigir.
Regra de domínio associada: o REGISTRO da pessoa nunca é apagado pela rotina — só a foto.

### Rótulo do módulo "Ministério" deveria ser self-service por igreja (Fase 5) — 2026-07-24

Nem toda igreja chama esse módulo de "ministério" — tem quem use "departamento", "rede"
(caso da igreja piloto). Não existe um substantivo neutro que sirva pros três ao mesmo
tempo, e construir self-service (a igreja escolhe o próprio rótulo, tipo Slack renomeando
"canais") só compensa quando houver mais de uma igreja usando o sistema.

Solução por ora (piloto de 1 igreja): rótulo hardcoded em `frontend/src/lib/rotulosMinisterio.ts`
(`ROTULO_MINISTERIO`/`ROTULO_MINISTERIO_PLURAL`, hoje "Rede"/"Redes"), usado em toda cópia
visível das telas de ministério — o domínio/código/rotas continuam `ministerio` (mesmo
tratamento que `congregacao` recebeu quando o rótulo virou "Unidade", ver memória
`congregacao-virou-unidade-no-front`). Além do rótulo, os textos que usam artigo/gênero
(ex.: "Nova {rótulo}", "arquivar essa {rótulo}") assumem gênero feminino ("rede") — se o
rótulo mudar pra uma palavra masculina sem ajustar a concordância, o texto erra o gênero.

Quando abrir para outras igrejas (Fase 5): trocar a constante hardcoded por uma config por
igreja (ex.: `igreja.rotuloMinisterio` + gênero), com um passo de onboarding perguntando
"como sua igreja chama isso: Ministério, Departamento, Rede...?". Provável que o mesmo
padrão sirva pra "congregação/unidade/campus" (ver `igrejas-vinculadas`), então vale
desenhar as duas configs juntas nessa hora, não uma de cada vez. O mesmo raciocínio vale
pra "Célula" (spec `2026-07-25-celulas-design.md`) quando esse módulo existir.

### Endereço do encontro da célula (2026-07-25)

Spec de Células (`2026-07-25-celulas-design.md`) deixou de fora um campo de endereço de
onde a célula se reúne, porque na prática varia semana a semana (ex.: "essa semana é na
casa do Fulano"). Se o uso real pedir, vale pensar num histórico de local por encontro
(não um endereço fixo na célula), talvez até junto de uma feature de registro de
encontros/presença — não construir um campo fixo simples, já sabendo que não reflete a
realidade.

### Busca global (Elasticsearch) precisa acompanhar Visitantes/Células (2026-07-25)

Os specs `2026-07-25-visitantes-design.md` e `2026-07-25-celulas-design.md` não
mencionam a busca global unificada (Elasticsearch, `busca/` — o mesmo mecanismo que já
indexa pessoa/evento/movimentação via *transactional outbox*). Pendência a resolver
depois que os dois módulos estiverem implementados:

- **Visitante**: provavelmente precisa entrar no índice (buscar visitante pelo nome na
  busca global), com o mesmo cuidado de permissão que já existe pra financeiro/usuários
  (`podeVerUsuariosEFinanceiroNaBuscaGlobal`) — decidir quem pode ver visitante na busca
  (`ADMIN_IGREJA` e `SECRETARIO`, pelo spec de capacidades extra).
- **Célula**: decidir se célula em si é uma entidade buscável (como `ministerio` deveria
  ser, verificar se já está) ou só aparece indiretamente via pessoa/visitante.
- Conferir se `ministerio` (Redes) já está indexado na busca global — se não estiver,
  é a mesma pendência, só que já existente antes destes três specs.
- Lembrar do outbox: toda entidade nova que entra na busca precisa emitir evento pro
  outbox nas operações de criar/atualizar/arquivar (mesmo padrão de pessoa/evento),
  senão o índice fica desatualizado silenciosamente.
