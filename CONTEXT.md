# CONTEXT — Domus

**O contexto de domínio deste repo vive no `domus/CLAUDE.md` da raiz** (roadmap, stack,
convenções, modelo de dados, princípios, guardrails de decisão). Este arquivo é só um
ponteiro pra evitar que skills e agentes procurem contexto em outro lugar.

**Onde está cada coisa (referência rápida):**

| O quê | Onde |
|---|---|
| Stack, princípios, guardrails, roadmap, ER | `domus/CLAUDE.md` (raiz) |
| Workflow (7 fases) | `domus/WORKFLOW.md` |
| Setup do Codex (esteira paralela) | `domus/docs/codex-setup.md` |
| Diretrizes de design (UX/UI/motion) | `domus/docs/design-guidelines.md` |
| Contexto local de back (stack Spring, testes back) | `backend/api/CLAUDE.md` |
| Contexto local de front (stack Next, testes front) | `frontend/CLAUDE.md` |
| Agents especializados | `domus/.claude/agents/` |
| Issue tracker (manual) | `domus/docs/agents/issue-tracker.md` |
| Triage labels (vocabulário) | `domus/docs/agents/triage-labels.md` |
| Domain docs (regras) | `domus/docs/agents/domain.md` |
| Decisões arquiteturais | `domus/docs/adr/` |
| Backlogs (3 visões) | `domus/docs/BACKLOG/` |
| Specs de design (pré-feature) | `domus/docs/specs/` |
| Planos de execução (tasks numeradas) | `domus/docs/plans/` |

**Antes de criar contexto novo:**

1. Checar se já está em `domus/CLAUDE.md` — se estiver, **não duplicar aqui**.
2. Se for decisão arquitetural que merece sobreviver ao contexto de conversa (ADR),
   criar arquivo em `domus/docs/adr/` no formato `YYYY-MM-DD-titulo-curto.md`.
3. Se for regra de design transversal (UX/UI/motion), adicionar a
   `domus/docs/design-guidelines.md`.

Ver `domus/docs/agents/domain.md` para regras completas de uso de domain docs.
