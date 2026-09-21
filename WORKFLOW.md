# Workflow de feature/task — Domus

Como eu (Claude) trabalho quando você pede uma feature ou task. **Sempre completo** —
mesmo pra coisa trivial, todas as fases rodam (rápido quando trivial). Você me cobra de
seguir; eu te cobro de aprovar antes de eu seguir pra próxima.

## Skills / agents do fluxo

| Fase | Skill / agent | O que produz |
|---|---|---|
| 0. Triagem | (julgamento interno) | Classificação: trivial / moderada / grande |
| 1. Auditoria | `Explore` agent em paralelo | Resumo do que entendi + gaps |
| 2. Grilling | `superpowers:grilling` + `AskUserQuestion` | Árvore de decisões, sem suposição silenciosa |
| 2.5. Pressão extra | `@grilling-reviewer` (Grande feature) | Tenta quebrar a proposta antes do brainstorm |
| 3. Brainstorm | `superpowers:brainstorming` | Abordagem, riscos, lista do que NÃO fazer |
| 4. Plano + Worktree | `superpowers:writing-plans` + `superpowers:using-git-worktrees` | `domus/docs/plans/YYYY-MM-DD-titulo.md` + branch |
| 5. Execução | `superpowers:subagent-driven-development` + agents do time | Código, testes, refatorações |
| 6. Verificação | `superpowers:verification-before-completion` + `@code-reviewer` (+ `@dev-cox` adversarial) | Checklist pré-commit |
| 7. Memória + retrospectiva | `@dev-docs` | Notas novas no MEMORY.md / backlog / ADRs |
| 8. Release | `@dev-release` | Merge develop → main, push. **Sem PR** — você é solo. |

## Regra-mãe (vinda do `domus/CLAUDE.md`)

**Não commitar antes de você testar.** Toda fase acaba em **espera explícita** pela sua
resposta. Eu aviso o que fiz e o que quero em seguida; você testa/aprova/corrige.

---

## Fase 0 — Triagem (15 segundos)

Antes de qualquer coisa, classifico em uma linha e te digo:

- **Trivial** (typo, 1 linha, dúvida conceitual): vou direto pra Fase 5 depois do Fase 1 rápido.
- **Moderada** (1-2 arquivos, regra clara, sem migration): Fase 1 + Fase 2 reduzida + Fase 5.
- **Grande** (3+ arquivos, back+front, migration, comportamento novo): todas as fases.

Mesmo no trivial, **todas as fases** rodam — só andam mais rápido. Custo: zero. Benefício:
você nunca vê um "ah, isso era trivial" terminando em bug de segurança por eu ter pulado
grilling.

## Fase 1 — Auditoria de contexto

Em paralelo, leio:
1. `domus/CLAUDE.md` (raiz) — focado nas seções do domínio da task.
2. `domus/docs/agents/issue-tracker.md` — pra localizar item no `domus/docs/BACKLOG/`.
3. Memórias em `~/.claude/projects/.../memory/` filtradas por palavra-chave.
4. Código que vou mexer (`mcp__idea__search_*`, `grep` cirúrgico).
5. Plan/spec existente em `domus/docs/{plans,specs}/` sobre o mesmo tema.

**Saída:** resumo de 5 linhas no chat, terminadas em "gaps conhecidos: A, B, C".
**Espera:** você confirma que o resumo está certo antes da Fase 2.

## Fase 2 — Grilling

Invoco `superpowers:grilling`. A skill calcula a fronteira de decisões abertas e me faz
perguntar em rounds numerados. Cada round: pergunto a **fronteira inteira** (várias
perguntas em uma só mensagem), recomendo resposta em cada uma, espero você responder.

**Não sigo pra próxima fase enquanto a fronteira não fechar.**

Quando uma pergunta precisa de fato do ambiente (código, schema, etc.), eu mesmo busco
com `Explore`/subagent — nunca pergunto a você o que posso descobrir sozinho.

## Fase 2.5 — Pressão extra (só pra Grande)

Invoco `@grilling-reviewer` (em `domus/.claude/agents/`): recebe a proposta pós-grilling
e tenta quebrá-la em 5 ângulos (segurança, UX, regra de negócio, consistência com
guardrails do CLAUDE.md, regressão). Devolve contraponto. Eu incorporo ou descarto com
justificativa.

**Espera:** você aprova a versão final da proposta.

## Fase 3 — Brainstorm

Invoco `superpowers:brainstorming`. Saída:
- Decisões de arquitetura (com porquê).
- Lista do que NÃO fazer (tão importante quanto o que fazer).
- Estimativa de tamanho (tasks vs. sub-tasks; cada task idealmente cabe numa sessão).
- Riscos conhecidos e como mitigar.

**Espera:** você aprova.

## Fase 4 — Plano + Worktree

1. Invoco `superpowers:writing-plans` → arquivo em
   `domus/docs/plans/YYYY-MM-DD-titulo-curto.md` com tasks numeradas.
2. Invoco `superpowers:using-git-worktrees` → cria worktree dedicada
   (`domus/.claude/worktrees/<slug>/`) e branch (`feat/<slug>` ou `fix/<slug>`).
3. Te aviso: "essa feature pede sessão nova na worktree X".

**Espera:** você confirma a branch e abre a sessão nova (ou pede pra continuar aqui, se
couber).

## Fase 5 — Execução

Invoco `superpowers:subagent-driven-development`. Cada task do plano vira uma rodada:
- Dispatch do agent especialista certo (ver lista abaixo).
- **MCP `context7`** disponível para todos os agents de código — consultar docs de
  Spring/Next/Flyway/Mercado Pago/etc. **antes** de confiar na memória de treinamento.
- Agents **sugerem** memória nova ao final (você aprova, regra do MEMORY.md).
- Cada task entregue passa pelo `@code-reviewer` antes de marcar como done.
- Regra do `domus/CLAUDE.md`: **pedaço testável por vez**, não tudo de uma vez.
- Tasks de UI/visual podem usar `@dev-design` (skill `impeccable`) **antes** do
  `@dev-front` implementar.

**Espera:** ao fim de cada task, eu te aviso com "pronto pra testar X". Você testa e me
diz OK ou corrige.

## Fase 6 — Verificação + commit

Invoco `superpowers:verification-before-completion`:
- Todos os testes verdes (`mvn -q test` no back, `npm run test` no front).
- Sem segredo no stdout (`domus/CLAUDE.md`).
- Mobile ajustado (se houver UI nova — `domus/docs/design-guidelines.md`).
- Mensagem de commit segue convenção do repo (Conventional Commits já praticado).
- **Commit único e coerente** (`domus/CLAUDE.md`: não vários parciais da mesma coisa).

**Opcional:** invoco `@dev-cox:adversarial-review` para uma segunda opinião antes de
commitar (esteira paralela via Codex CLI; pré-requisito `codex login status` →
autenticado — ver `domus/docs/codex-setup.md`).

**Espera:** você dá OK final e eu commito.

## Fase 7 — Memória + retrospectiva

Invoco `@dev-docs`. Ele te pergunta (uma vez, no fim):
1. Alguma coisa nova que merece virar nota de memória? (Sugiro rascunho, você aprova.)
2. Algum item novo pro backlog `domus/docs/BACKLOG/`? (Sugiro, você aprova.)
3. Algum guardrail novo que vale virar regra no `domus/CLAUDE.md`? (Sugiro, você
   aprova.)
4. Alguma decisão nova que merece virar ADR em `domus/docs/adr/`? (Sugiro, você aprova.)

## Fase 8 — Release (sem PR)

Invoco `@dev-release`. Ele cuida do caminho final até a produção. **Sem PR** — você é
solo.

**Fluxo:**
1. Pré-condições: worktree limpo, branch rebased em `develop`, testes verdes no back
   **e** no front, sem segredo em diff, Conventional Commits.
2. (Opcional) `@dev-cox:adversarial-review` se codex autenticado.
3. Merge da feature em `develop` (`--no-ff`).
4. Testes verdes em `develop`.
5. Merge de `develop` em `main` (`--ff-only` se possível).
6. Push `origin main`.
7. Limpeza do worktree e da branch local.

**Espera:** você confirma cada etapa.

---

## Lista de agents especializados (resumo)

Local (em `domus/.claude/agents/`):
- **`dev-back`** — Java 21, Spring Boot, padrão `controller→service→repo`, DTOs de
  retorno, soft delete, isolamento por igreja, Testcontainers para testes JPA.
- **`dev-front`** — Next.js, TanStack Query, RHF+Zod, padrões do repo (`Transicao`,
  `useFecharAnimado`, `card-interativo`, bottom-sheet mobile).
- **`dev-database`** — Schema Postgres, migrations Flyway, triggers plpgsql, índices,
  multi-tenant por coluna, LGPD-friendly. Use quando mexer em `V*.sql` ou entidade
  JPA.
- **`dev-design`** — Wrapper da skill `impeccable` (UX/UI/visual/motion). Use quando
  pedir pra desenhar/polir/redesenhar uma tela.
- **`dev-docs`** — Guardião do `domus/CLAUDE.md`, `WORKFLOW.md`, ADRs, retrospectiva.
  Roda a Fase 7.
- **`dev-release`** — Substitui o papel de PR. Worktree → develop → main → push.
- **`dev-cox`** — Ponte pro Codex CLI (esteira paralela: adversarial-review, rescue).

Globais (`~/.claude/agents/`):
- **`test-writer`** — Mockito puro por padrão; `@DataJpaTest` com
  `PostgresTestContainerSupport`; nomenclatura `snake_case` PT; um cenário = um teste.
- **`ux-reviewer`** — rótulo com placeholder concreto, prévia interativa, mobile,
  animação, acessibilidade básica. (Cópia local em `domus/.claude/agents/`.)
- **`security-reviewer`** — igreja_id do JWT (nunca do body), esconder no front
  não é esconder, validação de input, ordem de `requestMatchers`, segredos fora do log.
- **`code-reviewer`** — bugs latentes, simplificação, padrões do CLAUDE.md, cobertura
  real (não teste stubado).
- **`grilling-reviewer`** — quebra a proposta antes do brainstorm (opcional, só Grande).

## MCP skills / plugins carregados

- **context7** — MCP server. Todos os agents de código consultam docs de libs
  (Spring, Next, Flyway, Hibernate, Mercado Pago SDK) via `mcp__context7__*`.
- **codex** (plugin OpenAI) — para `@dev-cox` (adversarial-review, rescue).
- **impeccable** (skill global) — para `@dev-design` (UX/UI/motion).
