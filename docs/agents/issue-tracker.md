# Issue tracker — Domus

**Não usamos GitHub Issues nem CLI (`gh`/`glab`).** O tracker são arquivos markdown
versionados no próprio repo, atualizados a mão conforme features e dívidas aparecem.

## Onde mora

`docs/BACKLOG/` — três arquivos, cada um com escopo próprio:

- `docs/BACKLOG-DIVIDA-E-PROXIMO-SCOPE.md`
- `docs/BACKLOG-PRE-VENDA.md`
- `docs/BACKLOG-MELHORIAS-FUTURAS.md`

## Estados (não são labels, são marcadores inline)

Itens resolvidos ganham risquinho + marcador inline no próprio título:

- `~~texto~~ **RESOLVIDO (YYYY-MM-DD)**` — quando virou código/teste/feat e tá em produção
- `~~texto~~ **FEITO (YYYY-MM-DD)**` — sinônimo de RESOLVIDO (mantido por hábito)
- `~~texto~~ **CONCLUÍDA (YYYY-MM-DD)**` — sinônimo, comum em specs

Itens ainda abertos continuam como `- **Título.**` sem risquinho.

## Como adicionar item

Anotar no backlog certo (dívida → DIVIDA; pré-venda → PRE-VENDA; melhoria → MELHORIAS)
com a justificativa em uma linha e o link pro contexto (memória, spec, plan) se houver.
Sem número de issue, sem label.

## Consumer rules para skills vizinhas

`triage`, `to-tickets`, `to-spec`: ler **primeiro** este arquivo. Não tentar `gh issue
list` — não vai achar nada. Operar sobre os arquivos `BACKLOG-*.md` em
`docs/BACKLOG/`.
