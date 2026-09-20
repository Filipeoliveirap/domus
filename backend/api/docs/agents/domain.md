# Domain docs — Domus

## Layout: single-context (com ponteiro pro CLAUDE.md existente)

O contexto principal do projeto já vive no `CLAUDE.md` da raiz do repo (roadmap,
convenções, modelo de dados, princípios). **Não duplicar** — `CONTEXT.md` aponta pra lá.

### `CONTEXT.md` na raiz

Um arquivo curto, só dizendo "leia `CLAUDE.md`". Skills de domínio (`to-spec`, planos em
`docs/superpowers/plans/`) seguem esse redirecionador em vez de criar uma cópia paralela.

### `docs/adr/`

Diretório existe vazio. Criar arquivo aqui quando uma decisão arquitetural for tomada em
brainstorm e merecer ficar registrada pra releitura (estilo ADR — título, contexto,
decisão, consequências). Não é pra decisões triviais; é pra "se eu perder essa info, vou
tomar a decisão errada de novo daqui a 6 meses".

## Consumer rules para skills vizinhas

- Antes de criar contexto de domínio novo, **checar `CLAUDE.md`** — o que você vai
  escrever já está lá?
- Antes de criar ADR novo, **checar `docs/adr/`** — já existe decisão sobre o mesmo
  assunto?
- `CONTEXT.md` é índice, não conteúdo. Se crescer demais, é sinal de que algo deveria
  voltar pro `CLAUDE.md` ou virar ADR.
