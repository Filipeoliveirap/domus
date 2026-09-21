# CONTEXT — Domus

**O contexto de domínio deste repo vive no `CLAUDE.md` da raiz** (roadmap, stack,
convenções, modelo de dados, princípios, guardrails de decisão). Este arquivo é só um
ponteiro pra evitar que skills e agentes procurem contexto em outro lugar.

**Onde está cada coisa (referência rápida):**

| O quê | Onde |
|---|---|
| Stack, princípios, guardrails, roadmap, ER | `CLAUDE.md` (raiz) |
| Workflow (7 fases) | `WORKFLOW.md` |
| Setup do Codex (esteira paralela) | `docs/codex-setup.md` |
| Diretrizes de design (UX/UI/motion) | `docs/design-guidelines.md` |
| Contexto local de back (stack Spring, testes back) | `backend/api/CLAUDE.md` |
| Contexto local de front (stack Next, testes front) | `frontend/CLAUDE.md` |
| Agents especializados | `.claude/agents/` |
| Issue tracker (manual) | `docs/agents/issue-tracker.md` |
| Triage labels (vocabulário) | `docs/agents/triage-labels.md` |
| Domain docs (regras) | `docs/agents/domain.md` |
| Decisões arquiteturais | `docs/adr/` |
| Backlogs (3 visões) | `docs/BACKLOG/` |
| Specs de design (pré-feature) | `docs/specs/` |
| Planos de execução (tasks numeradas) | `docs/plans/` |

**Antes de criar contexto novo:**

1. Checar se já está em `CLAUDE.md` — se estiver, **não duplicar aqui**.
2. Se for decisão arquitetural que merece sobreviver ao contexto de conversa (ADR),
   criar arquivo em `docs/adr/` no formato `YYYY-MM-DD-titulo-curto.md`.
3. Se for regra de design transversal (UX/UI/motion), adicionar a
   `docs/design-guidelines.md`.

Ver `docs/agents/domain.md` para regras completas de uso de domain docs.
