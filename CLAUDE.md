# Domus — entrada raiz para o Claude Code

> **Você está na raiz do monorepo** (`~/Documents/domus/`). Este é o **ponto de
> entrada** — não a fonte da verdade. O contexto de domínio detalhado vive em outros
> arquivos; este mapa evita que você releia tudo toda vez.

## Onde mora o quê

| O quê | Onde ler |
|---|---|
| **Roadmap, princípios, guardrails de decisão, modelo de dados (ER), stack, segurança, fases** | `backend/api/CLAUDE.md` — é o mais completo. Ler inteiro antes da primeira task. |
| **Stack Next.js + convenções de teste front** | `frontend/CLAUDE.md` |
| **Workflow de feature (8 fases, agents, skills)** | `WORKFLOW.md` |
| **Setup do Codex (esteira paralela de revisão)** | `docs/codex-setup.md` |
| **Diretrizes transversais de design (UX/UI/motion)** | `docs/design-guidelines.md` |
| **Contexto/issue tracker/ADRs** | `CONTEXT.md` |
| **Agents especializados do time** | `.claude/agents/` |
| **Specs de design (pré-feature) e planos de execução** | `docs/specs/`, `docs/plans/` |
| **Backlogs (3 visões: dívida, pré-venda, melhorias)** | `docs/BACKLOG/` |
| **Domínio: regras de uso de context/ADR** | `docs/agents/domain.md`, `docs/agents/issue-tracker.md`, `docs/agents/triage-labels.md` |

## Regra-mãe (do `backend/api/CLAUDE.md`)

**Não commitar antes do autor testar.** Toda fase acaba em **espera explícita**.
Você avisa o que fez; o autor testa, aprova ou corrige.

## Sessão típica (TL;DR)

1. Autor pede feature.
2. Você roda o `WORKFLOW.md` (Fase 0 → Fase 8).
3. Você consulta **`docs/agents/issue-tracker.md`** antes de criar contexto novo.
4. Você consulta **`docs/agents/domain.md`** antes de criar ADR/contexto novo.
5. Você consulta **`docs/design-guidelines.md`** antes de desenhar/UX/UI.
6. Você consulta **`docs/codex-setup.md`** se a Fase 6 for usar adversarial review.

## Memória de longo prazo

Suas memórias persistentes moram em
`~/.claude/projects/-home-jos-filipe-oliveira-pereira-Documents-domus/memory/`.
O índice é `MEMORY.md`. Antes de gravar uma memória nova, **checar se já existe**
(memórias duplicadas viram ruído).

## Onde NÃO mexer sem avisar

- **Settings locais** (`backend/api/.claude/settings.json`, hooks, sessions,
  completions, templates, worktrees) — pertencem ao Harness, são gitignored.
- **Pastas `.env`** — segredos nunca sobem.
- **`docs/adr/`** — cada arquivo é uma decisão de brainstorm aprovada; não editar
  sem propor mudança na retrospectiva.
