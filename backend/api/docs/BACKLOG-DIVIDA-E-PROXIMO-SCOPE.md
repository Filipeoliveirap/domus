# Backlog — Dívida técnica & itens de próximo scope

> Lugar único para registrar **dívidas técnicas conscientes** e **features/itens deixados
> para depois** (fora do scope do piloto "minha igreja"). Sempre que uma decisão adiar algo,
> anotar aqui — para não se perder e resgatar quando abrir o próximo scope.
>
> Isto **não** substitui o roadmap (`CLAUDE.md`): o roadmap é o que vamos fazer agora, nas
> fases do piloto. Este arquivo é o que ficou **de fora** ou **para depois**.

---

## Dívida técnica (adiada de propósito, YAGNI/tempo)

- ~~**Cache de `Usuario` no `SecurityFilter`.**~~ **RESOLVIDO** (2026-08-20):
  `PrincipalCacheService` (`@Cacheable("principal")`, Redis, TTL 5 min) cacheia só os
  campos que o principal autenticado realmente usa (id, igreja.id, pessoa.id, role —
  conferido por grep em todo o projeto), reidratados num `Usuario` "casca" via builder —
  `instanceof Usuario`/`.getId()`/`.getIgreja().getId()`/`.getRole().getNome()` continuam
  funcionando sem tocar em `UsuarioAutenticado` nem controllers. `UsuarioService` invalida
  a chave exata (`CacheEvictor.evict`) em todo ponto que já invalidava a lista `usuarios`
  (ativo, role, arquivar, reativar, restaurar, excluir definitivo) — revogar acesso ou
  trocar role vale na próxima requisição, não espera o TTL. Validado ao vivo: chave
  `principal::<id>` some do Redis no instante da ação (não só depois do TTL). Aproveitado
  pra subir os TTLs dos caches de lista (`usuarios`/`pessoas`/`eventos`/`categorias`/
  `movimentacoes`) de 5 para 30 min — todos já tinham eviction cobrindo 100% dos pontos de
  escrita, então o TTL curto não protegia nada, só gerava mais miss.

- ~~**Infra de teste de banco (Testcontainers).**~~ **RESOLVIDO** (2026-08-20):
  `PostgresTestContainerSupport` (`src/test/java/.../shared/testcontainers/`) — interface
  com o container Postgres (`postgres:16-alpine`) como campo estático + `@DynamicPropertySource`,
  implementada pelas 34 classes `@DataJpaTest`/`@SpringBootTest`. Sobe um container só por
  execução do `mvn test` (Surefire deste projeto roda numa JVM só), migrations do Flyway
  aplicam sozinhas. `mvn test` não depende mais do `.env`/Neon — só de Docker rodando.
  Escolhido em vez de H2 de propósito: o schema usa trigger em plpgsql (regra dos 2 níveis
  de igreja), extensão `unaccent`, `gen_random_uuid()` — H2 daria falso positivo nesses
  testes (passa no H2, quebra no Postgres real). Achado no processo: 3 testes de
  `MigracaoV3Test` assumiam que sempre existia uma `igreja` no banco (verdade no Neon
  compartilhado, falso num banco isolado que começa vazio) — corrigido criando o próprio
  fixture em vez de depender de dado alheio.

- ~~**Maven wrapper quebrado.**~~ **RESOLVIDO** (2026-08-16): `.mvn/wrapper/maven-wrapper.properties`
  existe, `./mvnw` roda normalmente.

- **CSP: `unsafe-eval` removido, `unsafe-inline` (script) fica — nonce por requisição
  não serve pra esse app (2026-08-19).** Tentativa real de nonce+`strict-dynamic` via
  `proxy.ts` (Next 16, ex-`middleware.ts`): funcionou tecnicamente (nonce novo por
  requisição, header CSP certo), mas **quebraria o site inteiro**. Motivo: a maior parte
  das rotas é gerada **estaticamente** no build (`○` em `next build`) — o nonce só é
  aplicado em página renderizada por requisição, então nenhum `<script>` do Next em
  página estática ganha o nonce. Com `'strict-dynamic'` na regra, o navegador **ignora**
  `'self'`/`'unsafe-inline'` e exige nonce em todo script — sem ele em página estática,
  o próprio bundle JS do site (e o script do Google) seria bloqueado. Confirmado testando
  local (`next build && next start`, curl no header e grep por `nonce=` nas `<script>` —
  zero, apesar do header vir certo). Forçar renderização dinâmica em tudo resolveria, mas
  custa a performance que a geração estática dá — fora de escopo dessa correção.
  **O que ficou feito de verdade:** `unsafe-eval` saiu do `script-src` (checado o bundle de
  produção inteiro, `.next/static/chunks`, sem nenhum uso de `eval()`/`new Function()` —
  era concessão sem necessidade real). `unsafe-inline` (script) e `unsafe-inline` (style,
  por causa do `next/font`) continuam — hardening de verdade exigiria repensar quais rotas
  podem virar dinâmicas, não é ajuste de configuração.

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

- ~~**`X-Forwarded-For`: pegamos o PRIMEIRO elemento da lista.**~~ **RESOLVIDO** (2026-07-29):
  `RateLimitFilter.resolverIp()` agora pega o **último** elemento, não o primeiro — correto
  tanto se o proxy substitui quanto se acrescenta ao header. Coberto por
  `trustForwardedFor_usaUltimoIpDoHeader` e um teste simulando um atacante variando o
  primeiro elemento a cada requisição (`RateLimitFilterTest`).

- ~~**Rate limiting não conta requisições barradas pelo CSRF.**~~ **RESOLVIDO**
  (2026-08-20): `RateLimitFilter` movido para antes do `CsrfFilter`
  (`.addFilterBefore(rateLimitFilter, CsrfFilter.class)` em `SecurityConfig`) — agora toda
  requisição, inclusive a barrada por CSRF (403), conta no limite. Provado por
  `RateLimitCsrfOrderTest` (semeia o contador no Redis e confirma 429 antes do 403, sem
  depender da virada do minuto da janela fixa). Efeito colateral descoberto no processo:
  `AuthCsrfConfigTest` batia nas mesmas rotas de auth-tier (limite 10/min) sem isolar o
  contador — execuções repetidas na mesma janela levavam 429 legítimo mascarando o que o
  teste queria provar. Corrigido com limpeza do Redis no `@BeforeEach` desse teste.

- ~~**HSTS do backend depende de `FORWARD_HEADERS_STRATEGY=framework` em prod.**~~
  **RESOLVIDO** (2026-08-20): confirmado que `/root/deploy/.env.prod` na VPS **não** tinha
  `FORWARD_HEADERS_STRATEGY` nem `RATELIMIT_TRUST_FORWARDED_FOR` — as duas ficaram no
  default (`none`/`false`) desde o deploy original, então o `HstsHeaderWriter` nunca
  escrevia o header em produção, e o rate limiting por IP estava usando o IP interno do
  túnel Cloudflare (mesmo "IP" pra todo mundo, balde único). Adicionadas as duas linhas ao
  `.env.prod` (backup do arquivo feito antes) e o container `domus-api-1` recriado
  (`docker compose up -d --force-recreate api`). Validado ao vivo:
  `curl -sI https://domusigreja.com.br/api/auth/me` agora traz
  `strict-transport-security: max-age=31536000 ; includeSubDomains`. Quem protege de fato
  é o HSTS do front (`next.config.ts`), que cobre a origem inteira; o do back era defesa em
  profundidade que ficou letra morta por ~1 mês (desde a Fase 1) sem ninguém notar.

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

- ~~**Coleções `@OneToMany` da `Igreja` não são lidas por ninguém.**~~ **RESOLVIDO**:
  `Igreja.java` não tem mais nenhuma coleção `@OneToMany` — foram removidas. Resolve junto
  o item de `CascadeType.ALL`/`orphanRemoval` listado mais abaixo (auditoria da feature de
  igrejas vinculadas): sem coleção mapeada, não há cascata de escrita pra remover.

- ~~**Aviso do Mockito (self-attaching agent).**~~ **RESOLVIDO** (2026-08-19):
  `maven-dependency-plugin` (goal `properties`) resolve `${org.mockito:mockito-core:jar}`
  no repositório local, e o `maven-surefire-plugin` usa isso como `-javaagent`. Warning
  sumiu (`mvn test` sem nenhuma ocorrência de "self-attaching"), suíte completa continua verde.

### Revisão de validação de input em toda entrada — item do CLAUDE.md, primeira passada 2026-08-20

Levantamento (agente de exploração) de todos os `*RequestDTO`/`*Request` usados como
`@RequestBody`, cobertura de Bean Validation, uso de `@Valid`/`@Validated`, e parâmetros
livres (`q`/`busca`/paginação) sem limite. Corrigido nesta passada:

- **Bug real**: `VisitanteController.moverParaCelula` sem `@Valid` e `MoverParaCelulaRequest.celulaId`
  sem `@NotNull` — ver seção do harness de teste acima.
- **`@Size` em texto livre sem limite**: `EventoRequest` (descricao 5000, titulo/localTexto 255,
  tipo 80, recorteEtario 40), `PessoaRequestDTO` (email 255, observacoes 5000), `VisitanteRequest`
  (observacoes 5000, telefone com `@Pattern` que faltava, quantidadeFilhos com `@Min`/`@Max`),
  `AgendarExclusaoRequest` (senha 255, googleIdToken 4096, nomeConfirmacao 255), DTOs de auth
  (`AuthenticationDTO`, `ChangePasswordDTO`, `ResetPasswordDTO`, `GoogleLoginDTO`,
  `GoogleRegistrarDTO`, `ForgotPasswordDTO`, `RegistrarIgrejaAdminRequest` — senha/token/email
  sem teto de tamanho), `VinculoDTOs.EntrarNaFamiliaRequest` (código).
- **Bug real #2**: `MovimentacaoRequestDTO.contribuintes` não tinha `@Valid` — Bean Validation
  só cascateia pra dentro de listas quando o campo tem `@Valid`, então o `@NotNull` de
  `ContribuinteDTO.valor`/`pessoaId` nunca era checado. Um contribuinte com `valor: null`
  estourava `NullPointerException` (500) em `MovimentacaoFinanceiraService.validarContribuintes`
  (`BigDecimal::add` na soma) em vez de 400. Corrigido com `@Valid` + `@Size(max=200)` no campo.
- **Bug real #3**: `CelulaRequest.horario` era `String` livre sem `@Pattern`; o service fazia
  `LocalTime.parse(data.horario())` sem tratamento — string malformada estourava
  `DateTimeParseException` não capturada (500) em vez de 400. Corrigido com
  `@Pattern` aceitando vazio/null ou `HH:mm`.
- **Listas sem limite**: `InscreverPessoasRequest.pessoaIds` ganhou `@Size(max=500)`.
- **Teto de paginação**: `spring.data.web.pageable.max-page-size=100` em
  `application.properties` — o default do Spring é 2000, então `?size=2000` funcionava em
  qualquer listagem paginada. Confirmado que nenhum `@PageableDefault` do projeto passa de 20.
- **`q`/`busca` sem limite**: `@Validated` na classe + `@Size(max=200)` no parâmetro, em
  `BuscaController` (6 endpoints), `VisitanteController`, `UsuarioController`,
  `PessoaController`, `EventoController`, `MovimentacaoFinanceiraController` (2),
  `CategoriaFinanceiraController`, `InscricaoController`. Exigiu um handler novo em
  `GlobalExceptionHandler` para `ConstraintViolationException` — violação de `@Validated` em
  `@RequestParam` não é `MethodArgumentNotValidException` (essa só cobre `@Valid` em
  `@RequestBody`); sem o handler, cairia no genérico e devolveria 500 em vez de 400.
- **Investigado e descartado como falso positivo**: `role`/`capacidade` como `String` livre
  (`UpdateRoleRequest`, `ConcederAcessoRequestDTO`, `CapacidadeRequest`) já são validados no
  service (`RoleRepository.findByNome` → 404 se inválido; `UsuarioService.validarCapacidade`
  → `BusinessException` 400) — não é bug, é validação na camada certa, não na anotação.
  `AdicionarMembroCelulaRequest` (pessoaId/visitanteId sem `@NotNull`) também é falso
  positivo: `CelulaService.adicionarMembro` já lança `BusinessException("MEMBRO_INVALIDO", ...)`
  quando os dois vêm nulos — regra XOR de negócio, não dá pra expressar em Bean Validation
  simples sem `@AssertTrue` num validador custom (avaliar se compensar depois).

~~**Ficou de fora desta passada**~~ **RESOLVIDO** (2026-08-20): módulos de
célula/ministério/local-evento/financeiro auditados campo a campo (agente de exploração).
Nenhum bug real encontrado — todos os controllers já usam `@Valid`, campos de texto livre
já têm `@Size`/`@NotBlank` onde cabe. Únicos pontos levantados e descartados por
julgamento (não são bugs, é regra hipotética que ninguém pediu):
`AdicionarMembroCelulaRequest.pessoaId`/`visitanteId` sem `@NotNull` (já é falso positivo
documentado acima — regra XOR resolvida no service); `LocalEventoRequest.capacidade` sem
`@Max` (só `@Positive`); `MovimentacaoRequestDTO.dataMovimentacao` sem limite temporal
(aceita data futura). Revisão de validação de input **concluída** para os módulos que
faltavam — item do CLAUDE.md pode ser marcado como feito.

---

## Segurança / autorização — a discutir (decisão de produto)

- ~~**Login CSRF (resíduo aceito na migração de cookie, 2026-07-16).**~~ **RESOLVIDO**
  (2026-08-20): as rotas públicas de auth (`/auth/login`, `/auth/google/login`,
  `/auth/google/registrar`, `/auth/forgot-password`, `/auth/reset-password`,
  `/igrejas/registrar`) saíram do `ignoringRequestMatchers` do CSRF em `SecurityConfig`, e
  agora exigem o double-submit token como qualquer rota autenticada. O obstáculo original
  (buscar token antes de cada form público) resolvido sem endpoint novo: o backend já faz
  resolução **eager** do token CSRF (`csrfTokenRequestHandler`, `setCsrfRequestAttributeName(null)`),
  então qualquer `GET`, mesmo 401, já grava o cookie `XSRF-TOKEN`. `authService`
  (`frontend/src/services/auth.service.ts`) ganhou `garantirCsrfCookie()`, chamada antes
  das 6 mutações públicas: dispara `GET /auth/me` só quando o cookie ainda não existe
  (`document.cookie` sem `XSRF-TOKEN=`), e o axios já anexa `X-XSRF-TOKEN` sozinho (default
  do axios pra requisição same-origin — sem precisar de interceptor manual). Testado:
  backend (`AuthCsrfConfigTest`, POST sem token → 403, com token → passa da camada CSRF) e
  ao vivo no navegador + curl simulando o fluxo do axios — as 4 telas (login, cadastro,
  esqueci senha, reset de senha) funcionando normalmente, inclusive no pior caso (visita
  fria numa página sem passar por `/login` antes, sem cookie nenhum).

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

- ~~**Acesso horizontal a dados de pessoa dentro da mesma igreja.**~~ **RESOLVIDO**
  (decisão do autor em 2026-07-29): endereço e observações (possíveis notas pastorais
  privadas) ficam restritos a quem `Permissoes.podeVerDadosSensiveisDePessoa` libera
  (ADMIN_IGREJA ou SECRETARIO) — telefone e data de nascimento continuam abertos a
  qualquer pessoa da igreja (decisão explícita, não é o resto ficando de fora por
  esquecimento). A implementação (`PessoaResponse.from(..., incluirDadosSensiveis)`,
  já usada em `GET /pessoas` e `GET /pessoas/{id}`) já existia — faltava só teste
  provando; adicionado em `PessoaServiceTest` (`buscarPorId_semDadosSensiveis_...`).
- ~~**Revisão de autorização por perfil, módulo a módulo.**~~ **RESOLVIDO** (confirmado
  pelo autor em 2026-08-20): feito via brainstorm próprio, cobrindo "quem vê o quê" nos
  módulos do sistema.

---

## Frontend — robustez de sessão (a vigiar)

- ~~**Logout indevido ao falhar refresh (a vigiar).**~~ **INSTRUMENTADO** (2026-08-20):
  ainda sem repro, então não dava pra "consertar" às cegas — mas em vez de continuar só
  observando, `encerrarSessao()` (`src/lib/api.ts`) agora manda um `Sentry.captureMessage`
  toda vez que é chamada por falha de refresh (não por logout normal), com a URL que
  disparou o 401 original. Se reaparecer em produção, o Sentry tem o dado pra investigar
  de verdade em vez de ficar só "observando". Histórico original mantido: descoberto em
  2026-07-16 (MEMBRO em `/financeiro/movimentacoes` era deslogado por query de admin sem
  gate de permissão, `useCategoriasSelect()`) e corrigido gateando por `autorizado` +
  `AuthGuard` no layout `(app)`; a pulga que ficou era a corrida teórica na
  rotação/detecção de reuso do refresh, que segue sem repro.
- ~~**403 de CSRF não tem caminho de recuperação no front (a vigiar).**~~ **RESOLVIDO**
  (2026-08-20): o `accessDeniedHandler` do `SecurityConfig` era o mesmo pra falha de CSRF
  **e** pra negação de role em `requestMatchers` (`.hasAnyRole(...)`) — os dois rodam antes
  do `DispatcherServlet`, então nunca chegavam no `GlobalExceptionHandler`, e o 403 saía
  sem corpo nos dois casos, indistinguíveis. Agora `responderAcessoNegado` checa o tipo da
  exceção (`CsrfException` vs. resto) e devolve `codigo=CSRF_INVALIDO` ou `ACESSO_NEGADO`
  no mesmo formato `ErrorResponse` do resto da API. O interceptor do `api.ts` trata só o
  primeiro caso: busca um XSRF-TOKEN novo (`GET /auth/me`, single-flight igual ao refresh
  de access token) e reenvia a requisição original uma vez (`_retryCsrf`, evita loop). Um
  403 `ACESSO_NEGADO` continua batendo direto no erro — não mascara negação de permissão
  como se fosse token velho. Testado em `AuthCsrfConfigTest`
  (`negacaoDeRoleE403ComCodigoAcessoNegadoNaoCsrf` prova que os dois códigos não se
  confundem).

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

## Deploy / operação — gotchas descobertos ao vivo

- **Run do GitHub Actions pode ficar travada em `queued` para sempre (2026-08-19).** O
  workflow "Build e publicar imagens" (dispara em todo push pra `main`) ficou quase 8h em
  `queued`, sem nenhum step iniciar — não é billing/quota (backup do Postgres, mesmo
  runner `ubuntu-latest`, rodou normal no meio desse intervalo) nem outage do GitHub
  (status page limpo). Parece um bug pontual do lado do GitHub numa run específica.
  `gh run cancel` nela devolveu **erro 500** — nem cancelar dava. **Solução:** disparar
  uma run nova manualmente (`gh workflow run build-images.yml --ref main`, o workflow já
  tem `workflow_dispatch`) — pega runner normal, a antiga só fica de lixo visual no
  histórico. Se o deploy não completar depois de um merge aprovado, checar
  `gh run list --workflow=build-images.yml` antes de supor que o problema é no código.

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
- ~~**`Endereco` (`@Embeddable`) mora em `modules.membro`**.~~ **RESOLVIDO** (nota
  desatualizada, confirmado 2026-08-19): já vive em `shared.dominio.Endereco`, reusado
  por `Igreja`, `Pessoa`, `Visitante` e `LocalEvento`. O move já tinha acontecido em
  algum momento sem essa linha ser atualizada.
- **Listas navegáveis** (a mãe folhear membros/eventos das filhas) — continua fora, como o
  spec já dizia. Depende de consultar o pastor primeiro. ~~**Irmãs verem eventos umas das
  outras**~~ **RESOLVIDO** (2026-07-29): eventos compartilhados entre igrejas vinculadas
  (`evento.restrito_propria_igreja`), ver spec `2026-07-28-eventos-compartilhados-design.md`.
- ~~**Upload do logo da igreja.**~~ **RESOLVIDO**: entrou junto com o upload de foto da Fase 2
  (V2, 2026-07-22) — `<UploadFoto>` em `/configuracoes/igreja`, `igreja.logo_foto_id` é FK
  pra `foto.id` (nota antiga desta linha estava desatualizada, escrita antes da entrega).
- ~~**Confirmações usam `window.confirm`.**~~ **RESOLVIDO** (2026-07-19): criado
  `components/common/ModalConfirmacaoCritica`, no modelo "digite o nome para confirmar" do
  GitHub — lista o que se perde e o que se mantém, e exige digitar o nome da igreja. Usado em
  desvincular (sede) e sair da família (congregação). **Reutilizável**: é o componente a usar em
  toda ação destrutiva daqui pra frente (ex.: excluir conta e exclusão definitiva da Fase 3).

### Auditoria de segurança da feature (2026-07-19) — resíduos

Dois agentes revisaram back e front. 9 dos 11 achados foram corrigidos na hora. Ficaram:

- ~~**Focus trap no `ModalConfirmacaoCritica`.**~~ **RESOLVIDO** (2026-08-20): `Tab`/`Shift+Tab`
  agora ficam presos nos elementos focáveis do `formRef` (mesmo padrão de outros diálogos),
  excluindo os desabilitados — testado ao vivo (`Arquivar categoria` desabilitado até digitar
  a confirmação: o ciclo vai só entre o input e "Cancelar", nunca escapa pro fundo).
- ~~**Semântica ARIA das abas em `/financeiro/relatorios`.**~~ **RESOLVIDO** (2026-08-20):
  `role="tab"`/`aria-selected`/`aria-controls` nos botões, `role="tabpanel"` +
  `aria-labelledby` envolvendo o conteúdo que muda com a aba, e navegação por
  `ArrowLeft`/`ArrowRight`/`Home`/`End` (move o foco **e** troca a aba — testado ao vivo:
  seta direita em "Minha igreja" move o foco pro botão "Unidades" e troca o painel).
  (Em `/configuracoes` já tinha sido corrigido antes: eram links de navegação e viraram
  `aria-current="page"`.)
- ~~**`CascadeType.ALL` + `orphanRemoval` nas 5 coleções de `Igreja`.**~~ **RESOLVIDO**: as
  coleções foram removidas de `Igreja.java` (ver item equivalente mais acima nesta lista).
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
acima na Spec B (capacidade impondo limite de vagas) e as Specs C/D/E.

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

## Harness de teste de autorização por endpoint — FEITO (2026-08-20)

Ao adicionar os matchers de `/eventos/*/inscricoes**` no `SecurityConfig` (2026-07-20), a Task 2
original previa um teste MockMvc batendo na `SecurityFilterChain` de verdade
(`membroPodeSeInscreverEmEvento`/`membroNaoVeListaDeInscritos`), mas o harness não existia:
`SecurityFilterTest` é um teste unitário do `SecurityFilter` (o JWT filter) com Mockito puro —
não sobe contexto Spring, não passa pela `authorizeHttpRequests`, então não tinha como pegar
bug de **ordem** de `requestMatchers`. Erro de ordenação de matcher (curinga genérico casando
antes da regra específica) compilava limpo e passava em todos os testes unitários, só aparecendo
testando ao vivo (curl/Postman) — foi assim que a armadilha de `/igrejas/*` foi descoberta.

**Resolvido:** `AutenticacaoTestSupport` (`src/test/java/.../shared/security/`) + padrão
`@SpringBootTest @AutoConfigureMockMvc` — gera JWT real via `TokenService` num cookie
`domus_access` (o `SecurityFilter` do projeto lê o cookie na mão, não usa `@WithMockUser`) e
anexa CSRF via `csrf()` do `spring-security-test` (já estava no `pom.xml`, não era usado).
Piloto em `VisitanteControllerTest`, cobrindo validação (`@Valid` não acionado — o mesmo bug
real corrigido em `MoverParaCelulaRequest` nesta sessão) e autorização (403 por perfil, 401 sem
sessão, 403 sem CSRF). Documentado na tabela de convenções de teste do `CLAUDE.md`.

**Ficou de fora:** aplicar o harness aos demais controllers — hoje só `Visitante` está coberto.
Expandir módulo a módulo conforme for mexendo neles, não de uma vez.

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
- ~~**`adicionarAcompanhante` não tem `usuarioId` real no log**~~ **RESOLVIDO** (nota
  desatualizada, confirmado 2026-08-19): o método já recebe `usuarioId` como parâmetro
  próprio e o `log.info` já usa ele, não `meuMembroId`.

### ~~Contribuintes: filtro, relatório e múltiplos por lançamento~~ (2026-07-22, **RESOLVIDO**)

**FEITO** (confirmado 2026-08-19): `movimentacao_financeira.pessoa_id` virou tabela
`movimentacao_contribuinte` N-para-N (migration V15, `UNIQUE(movimentacao_id, pessoa_id)`,
com `valor` próprio por contribuinte — resolve o rateio vs. repetição citado abaixo).
`MovimentacaoContribuinte`, `MovimentacaoContribuinteRepository` e uso em
`RelatorioRepository`/`MovimentacaoFinanceiraService` confirmados no código.

Texto original mantido abaixo por contexto:

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

- ~~**Revisar `next/image` nas telas de foto.**~~ **PARCIALMENTE RESOLVIDO** (2026-08-19):
  os 13 avatares/logos de tamanho **fixo** (36-56px: modais de inscrição/quem vai/usuário,
  Sidebar, TopBar, início, ministérios, drawer de pessoa, lista de pessoas, lista de
  inscritos) viraram `next/image` com `width`/`height` numéricos batendo o px do CSS
  (`unoptimized`, porque `/api/fotos/{id}` exige cookie de sessão que o otimizador de
  imagem do Next não repassa na busca server-side — ver nota abaixo). Ficaram de fora
  **de propósito**, por serem imagem grande/responsiva sem tamanho fixo seguro pra
  hardcodar: banner de evento (`ModalEventoResumo.capaFoto` — tem aviso explícito no
  código sobre `z-index`/stacking context com o botão de fechar, `fill` violaria isso),
  os dois visualizadores em tela cheia (`VisualizadorFoto`, `viewerImg` de pessoas e
  ministérios), e `UploadFoto` (tamanho vem de prop, variável por chamada).
  ⚠️ **Gotcha registrado pra não esquecer:** o otimizador embutido do `next/image`
  busca a imagem no servidor sem o cookie httpOnly do navegador — usar `fill`/otimização
  de verdade nessas fotos (em vez de `unoptimized`) quebraria o carregamento por 401.

- **CDN de borda para `/fotos/{id}`.** Toda imagem passa pela própria API hoje; a resposta
  é `Cache-Control: immutable`, então cada navegador busca uma vez só — suficiente no
  tamanho de uma igreja. Se o volume um dia incomodar, a saída é colocar o Cloudflare na
  frente com cache de borda, sem mexer no modelo (o id nunca é reaproveitado, então cache
  de borda não tem problema de invalidação).

### ~~Separar as credenciais de backup das de foto~~ (2026-07-22, **RESOLVIDO** — confirmado pelo autor em 2026-08-19)

Hoje o backup usa `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`/`R2_BUCKET` (secrets do GitHub)
e as fotos usam `R2_FOTOS_*` (`.env` da VPS) — nomes e locais diferentes, tokens já separados.

Texto original mantido abaixo por contexto:

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

### ~~⚠️ Limpeza de foto de arquivada pode falhar por FK RESTRICT~~ — 2026-07-22, **RESOLVIDO 2026-07-29**

`LimpezaFotosJob.limparDeArquivadas` chamava `fotoService.remover` (que faz `DELETE FROM
foto`) sem antes soltar `pessoa.foto_id` — como essa coluna é `ON DELETE RESTRICT` (V2) e a
pessoa arquivada ainda referenciava a foto, o DELETE era recusado pelo banco todo dia, sem
alerta. Corrigido com `PessoaRepository.desvincularFoto` (nativa, porque `Pessoa` tem
`@SQLRestriction` e um UPDATE via JPQL não enxergaria a pessoa arquivada), chamada antes de
`fotoService.remover`. Provado contra Postgres real em
`PessoaRepositoryDesvincularFotoTest` (arquiva pessoa com foto, roda o UPDATE, confirma
`foto_id NULL` via query nativa) — exatamente o cenário que o Mockito não pegava.
Regra de domínio associada continua valendo: o REGISTRO da pessoa nunca é apagado pela
rotina — só a foto.

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

**PARCIALMENTE RESOLVIDO** (confirmado 2026-08-19): `VisitanteSearchRepository`,
`CelulaSearchRepository` e `MinisterioSearchRepository` existem — os três módulos já
estão indexados e entram em `BuscaGlobalService.buscar`.

~~⚠️ **Gap: `BuscaGlobalService.buscar` não checava permissão pra visitante.**~~
**RESOLVIDO** (2026-08-19): a chamada de `buscaVisitanteService.buscar` rodava
incondicionalmente pra qualquer role, enquanto usuário/financeiro já eram gateados por
`Permissoes.podeVerUsuariosEFinanceiroNaBuscaGlobal`. Envolvida no mesmo tipo de `if`,
usando `Permissoes.podeGerenciarVisitantes(role, capacidadesExtras)` (mesma regra já
aplicada em `VisitanteController`). Testes em `BuscaGlobalServiceTest`
(`acessoComumNaoVeVisitanteNaBuscaGlobal`, `adminVeVisitanteNaBuscaGlobal`,
`secretarioVeVisitanteNaBuscaGlobalMesmoSemSerAdmin`).

Célula: ainda não decidido se é buscável diretamente ou só via pessoa/visitante — pendente.

### ~~Excluir igreja: contas só-Google sem caminho de reautenticação na UI~~ (2026-08-18, resolvido)

Achado no review final da feature de exclusão de igreja (carência de 10 dias): o backend
já suportava as duas formas de reautenticar (senha nativa OU `googleIdToken`), mas
`ModalExcluirIgreja` só mandava `senha`, deixando contas só-Google sem caminho de
confirmação. Resolvido: `GET /igrejas/exclusao/resumo` agora expõe `temSenhaNativa`
(`ExclusaoIgrejaService.resumo`, a partir de `usuario.senhaHash`); o modal mostra o campo
de senha OU o botão `GoogleLogin` (reusando `@react-oauth/google`, mesmo componente do
`/login`) conforme esse campo.

### Testes automatizados de frontend (Vitest + React Testing Library) — pendente

Levantado durante a Spec 2 de convite público de evento (2026-08-22): hoje o front não tem
Jest/Vitest/Cypress/Playwright configurado (já listado como dívida técnica conhecida no
`CLAUDE.md`) — toda validação é manual no navegador. O `tsc --noEmit` pega erro de tipo
(assinatura errada, campo inexistente), mas não pega erro de **comportamento**: prop de
componente chutada errado (só descoberta se alguém clicar naquele caminho específico),
mutation/branch condicional errado, invalidação de query key faltando.

**Proposta:** configurar Vitest + React Testing Library (mais leve que Jest pra App Router)
depois que o fluxo visual de uma feature já estiver validado manualmente pelo autor — não
travar a entrega da feature pra montar a infra primeiro. Escopo inicial sugerido: hooks de
mutation (`useCriarConvidado`, `useGerarConvite`, etc.) e componentes com mais estado
condicional (`ModalInscreverAlguem`, com a pergunta "você também vai participar?"), não a
base de código inteira de uma vez.

### Desconectar conta Mercado Pago: sem confirmação nem aviso do que isso implica

Achado testando o fluxo de pagamento contra o sandbox do Mercado Pago (2026-08-25): o
botão de desconectar a conta de recebimento (`useDesconectarMercadoPago` /
`DELETE /pagamentos/conta`) executa na hora, sem `ModalConfirmacao` nem explicar a
consequência prática (eventos pagos com essa conta deixam de conseguir cobrar até
reconectar; inscrições já pagas não são afetadas, mas isso não fica dito em lugar nenhum).
Corrigir seguindo o padrão de confirmação já usado em outras ações destrutivas do projeto
(ver `ui-notificar-e-confirmacao` — não é `window.confirm` nem toast do sonner).
`/login`) conforme esse campo.

### Prazo do link de pagamento ("enviar link") fixo em 48h — devia acompanhar o evento

Levantado pelo autor testando o Plano 4b (2026-08-26): o link de pagamento gerado por
"Enviar link pra pagar" (`CobrancaEventoService.PRAZO_LINK_COMPARTILHADO`, 48h fixas) não
tem relação nenhuma com o evento em si — ideal seria valer até não dar mais pra se
inscrever (hoje, na prática, até o evento começar). Depende da feature abaixo pra fazer
sentido de verdade.

### Prazo de inscrição opcional no evento (nova feature)

Ideia do autor (2026-08-26), ainda não desenhada: eventos com inscrição já fecham
naturalmente quando o evento começa (`situacao !== 'AGENDADO'` bloqueia — ver
`BotaoConfirmarPresenca`), mas alguns eventos precisam de um prazo de inscrição **anterior**
ao início (ex.: acampamento, evento que exige logística prévia). Em vez de um botão manual
"encerrar inscrições", a ideia é um campo opcional na `EVENTO` (algo como
`inscricao_ate`/`prazo_inscricao`, nulável) que, quando preenchido, some com a exigência
`situacao === AGENDADO` como segunda trava de "pode se inscrever". Isso também resolveria o
item acima: o prazo do link de pagamento passaria a acompanhar esse campo quando presente,
em vez do fixo de 48h. Precisa de brainstorm completo (schema, UI de cadastro, mensagem pro
usuário quando o prazo já passou mas o evento ainda não começou) antes de virar plano.

### Trocar evento entre pago↔gratuito com gente já inscrita (2026-08-26, ainda não desenhado)

Gap apontado pelo autor durante a sessão de endurecimento do pagamento (junto com os dois
abaixo, achados numa revisão de segurança/gaps pedida por ele). Hoje o cadastro de evento
deixa mudar `preco`/`requerInscricao` livremente mesmo com inscritos existentes — não há
regra nenhuma pro caso "evento era grátis, virou pago com gente já confirmada" nem
"evento era pago, virou grátis com cobranças pendentes/pagas em aberto". Precisa decidir
(brainstorm): quem já está inscrito antes da mudança fica isento? cobrança pendente que
vira "evento agora é grátis" cancela sozinha? evento pago virando grátis estorna quem já
pagou? Não é bounded — mexe em regra de negócio de `InscricaoService`/`CobrancaEventoService`
e provavelmente em confirmação explícita na tela de editar evento.

### Escolha de meio de pagamento + parcelamento por evento, considerando a taxa do Mercado Pago (2026-08-26, ainda não desenhado)

Ideia do autor: no cadastro de evento pago, deixar a igreja escolher quais meios de
pagamento aceitar (cartão/Pix) e, se cartão, quantas parcelas — hoje o Payment Brick libera
tudo sem nenhuma configuração por evento. Puxa consigo uma decisão de produto real: quem
absorve a taxa do Mercado Pago (~4-5% no cartão, menor no Pix, e sobe com parcelamento)? A
igreja embute no preço na hora de cadastrar, ou repassa pro pagador? Avaliado como feature
grande de verdade (mexe em cadastro de evento, `PaymentBrickCheckout`, e características de
UX/produto que só o autor decide) — **precisa de brainstorm/spec própria antes de
implementar**, não é uma mudança bounded.

### Taxa do Mercado Pago não aparece separada no financeiro (2026-08-26, recomendação dada, não implementada)

Consequência de a `MovimentacaoAutomaticaService` registrar o valor **bruto** da inscrição
como entrada — o que realmente cai na conta da igreja no Mercado Pago é menor (desconta a
taxa por transação). Recomendação já discutida com o autor: criar uma categoria própria
**"Taxas de pagamento"** (separada de "Eventos" — taxa é despesa operacional, não parte do
valor do evento) e registrar uma SAÍDA com o valor exato da taxa, usando o `fee_details` que
a API do Mercado Pago já devolve na mesma consulta que o webhook faz hoje
(`MercadoPagoApi.buscarInformacoesPagamento`) — só falta ler esse campo e persistir. Bounded
o suficiente pra implementar direto quando entrar na fila, sem brainstorm — mas faz mais
sentido resolver junto do item acima (a decisão de quem absorve a taxa muda o que "registrar
a taxa" significa na prática).

### `PagarCobrancaRequest` sem validação de bean nos campos (2026-08-26, decisão consciente de não mexer)

Achado na revisão de segurança do fluxo de pagamento: `token`/`paymentMethodId`/
`payerEmail`/`issuerId`/`installments` chegam no `POST /cobrancas/{id}/pagar` sem
`@NotBlank`/`@Size`/validação nenhuma antes de ir pro Mercado Pago. Não é explorável (o
valor cobrado sempre vem de `cobranca.getValor()` no servidor, nunca do request do
cliente) — decidido não mexer por ora. Se algum dia sobrar tempo de polimento: o único
ganho real é uma mensagem de erro mais amigável em vez do erro genérico que o Mercado
Pago devolve pra input malformado.

### Unificar "acompanhante" e "convidado sem cadastro" — dois modelos pro mesmo conceito (2026-08-26, ainda não desenhado)

Ao longo desta sessão (feature de pagamento + financeiro), toda lógica que precisa
resolver "quem é o pagador/contribuinte de uma inscrição" acabou com uma ramificação de
3 caminhos — `pessoa` / `acompanhante` / `convidado sem cadastro` — espalhada em
`CobrancaController`, `MercadoPagoWebhookService`, `InscricaoService` e
`MovimentacaoAutomaticaService`. `acompanhante` (`AcompanhanteInscricao`, entidade própria
aninhada em `InscricaoEvento.getAcompanhantes()`) é o modelo mais antigo do projeto e
**não tem campo de e-mail** — por isso nunca recebe comprovante de pagamento nem aparece
como contribuinte cadastrado em nada. `convidado sem cadastro` (`nomeConvidado`/
`emailConvidado`/`telefoneConvidado`, direto em `InscricaoEvento`, criado no Plano 4b)
resolve o mesmo problema — "alguém sem conta no Domus participando do evento" — só que
com e-mail desde o início. Os comentários `// Acompanhante (modelo antigo, ...)` espalhados
pelo código (`MercadoPagoWebhookService`, `InscricaoService`, `InscricaoEvento`) já
sinalizavam a divergência mesmo antes desta sessão.

Unificar os dois eliminaria essa ramificação de 3 caminhos em tudo que resolve "quem pagou"
e destravaria e-mail de comprovante pra quem hoje entra como acompanhante. Não é bounded:
mexe em modelo de dados (`AcompanhanteInscricao` provavelmente vira campos em
`InscricaoEvento`, ou o inverso), sem falar em todo código/frontend que já assume os dois
modelos separados — precisa de brainstorm completo antes de qualquer código.
