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
| 2.5. Pressão extra | agent `grilling-reviewer` (a criar) | Tenta quebrar a proposta antes do brainstorm |
| 3. Brainstorm | `superpowers:brainstorming` | Abordagem, riscos, lista do que NÃO fazer |
| 4. Plano + Worktree | `superpowers:writing-plans` + `superpowers:using-git-worktrees` | `docs/superpowers/plans/YYYY-MM-DD-titulo.md` + branch |
| 5. Execução | `superpowers:subagent-driven-development` + agents do time | Código, testes, refatorações |
| 6. Verificação | `superpowers:verification-before-completion` + `code-review` | Checklist pré-commit |
| 7. Memória + retrospectiva | (sugestão, você aprova) | Notas novas no MEMORY.md / backlog |

## Regra-mãe (vinda do CLAUDE.md)

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
1. `CLAUDE.md` (raiz) — focado nas seções do domínio da task.
2. `docs/agents/issue-tracker.md` — pra localizar item no `BACKLOG-*.md`.
3. Memórias em `~/.claude/projects/.../memory/` filtradas por palavra-chave.
4. Código que vou mexer (`mcp__idea__search_*`, `grep` cirúrgico).
5. Plan/spec existente em `docs/superpowers/{plans,specs}/` sobre o mesmo tema.

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

Invoco agent `grilling-reviewer` (a criar, em `~/.claude/agents/`): recebe a proposta
pós-grilling e tenta quebrá-la em 5 ângulos (segurança, UX, regra de negócio,
consistência com guardrails do CLAUDE.md, regressão). Devolve contraponto. Eu incorporo
ou descarto com justificativa.

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
   `docs/superpowers/plans/YYYY-MM-DD-titulo-curto.md` com tasks numeradas.
2. Invoco `superpowers:using-git-worktrees` → cria worktree dedicada (`../domus-<slug>/`)
   e branch.
3. Te aviso: "essa feature pede sessão nova na worktree X".

**Espera:** você confirma a branch e abre a sessão nova (ou pede pra continuar aqui, se
couber).

## Fase 5 — Execução

Invoco `superpowers:subagent-driven-development`. Cada task do plano vira uma rodada:
- Dispatch do agent especialista certo (`@dev-back`, `@dev-front`, `@test-writer`,
  `@ux-reviewer`, `@security-reviewer`, `@code-reviewer`).
- Agents **sugerem** memória nova ao final (você aprova, regra do MEMORY.md).
- Cada task entregue passa pelo `@code-reviewer` antes de marcar como done.
- Regra do CLAUDE.md: **pedaço testável por vez**, não tudo de uma vez.

**Espera:** ao fim de cada task, eu te aviso com "pronto pra testar X". Você testa e me
diz OK ou corrige.

## Fase 6 — Verificação + commit

Invoco `superpowers:verification-before-completion`:
- Todos os testes verdes (`mvn -q test`).
- Sem segredo no stdout (CLAUDE.md).
- Mobile ajustado (se houver UI nova).
- Mensagem de commit segue convenção do repo (Conventional Commits já praticado).
- **Commit único e coerente** (CLAUDE.md: não vários parciais da mesma coisa).

**Espera:** você dá OK final e eu commito + abro PR (se for pra `main`).

## Fase 7 — Memória + retrospectiva

Te pergunto (uma vez, no fim):
1. Alguma coisa nova que merece virar nota de memória? (Sugiro rascunho, você aprova.)
2. Algum item novo pro backlog `BACKLOG-*.md`? (Sugiro, você aprova.)
3. Algum guardrail novo que vale virar regra no `CLAUDE.md`? (Sugiro, você aprova.)

---

## Lista de agents especializados (resumo)

- **`dev-back`** (local, Domus): Java 21, Spring Boot, padrão `controller→service→repo`,
  DTOs de retorno, soft delete, isolamento por igreja, Testcontainers para testes JPA.
- **`dev-front`** (local, Domus): Next.js, TanStack Query, RHF+Zod, padrões do repo
  (`Transicao`, `useFecharAnimado`, `card-interativo`, bottom-sheet mobile).
- **`test-writer`** (global): Mockito puro por padrão; `@DataJpaTest` com
  `PostgresTestContainerSupport`; nomenclatura `snake_case` PT; um cenário = um teste.
- **`ux-reviewer`** (global): rótulo com placeholder concreto, prévia interativa, mobile,
  animação, acessibilidade básica.
- **`security-reviewer`** (global): igreja_id do JWT (nunca do body), esconder no front
  não é esconder, validação de input, ordem de `requestMatchers`, segredos fora do log.
- **`code-reviewer`** (global): bugs latentes, simplificação, padrões do CLAUDE.md,
  cobertura real (não teste stubado).
- **`grilling-reviewer`** (global, opcional): quebra a proposta antes do brainstorm.
