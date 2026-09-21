# Diretrizes transversais de design (UX/UI/motion)

Este arquivo é a **fonte da verdade** das regras de design do Domus que valem pra
qualquer tela nova, não importando o módulo. Ele consolida — pra evitar repetir a cada
módulo — o que as seções sujas de `domus/CLAUDE.md`, `backend/api/CLAUDE.md` e
`frontend/CLAUDE.md` dizem em parágrafos diferentes.

Quando uma regra for tocar a **implementação** de um componente específico, ela ainda
aparece (com mais detalhes) no `frontend/CLAUDE.md`. Quando tocar **conceito de
negócio**, ela aparece (com contexto) no `domus/CLAUDE.md`. Este arquivo é o índice
**horizontal** com a regra pronta pra aplicar.

**Workflow pra usar:** se você está prestes a desenhar uma tela/formulário/modal/drawer
novo, leia este arquivo inteiro. Pra review antes de merge, delegue ao
**`@ux-reviewer`** (em `domus/.claude/agents/`, cópia em
`~/.claude/agents/ux-reviewer.md`).

---

## 1. Responsividade é obrigatória

Toda funcionalidade nova (tela, formulário, modal, drawer, tabela) tem que ser ajustada
para **mobile** como parte da própria entrega — não é etapa separada nem opcional.

**Padrões vigentes:**
- Tabelas viram **cards** no mobile (cada linha vira bloco empilhado com label em cima
  e valor embaixo).
- Headers com título+botão **empilham** no mobile.
- Grids de formulário **colapsam** para 1 coluna.
- Modais/drawers reduzem padding interno no mobile.
- `min-width: 0` na cadeia flex/grid e larguras fixas (ex.: botão Google) revistas
  pra evitar overflow horizontal.
- Validar no viewport de celular **antes** de considerar pronto.

---

## 2. UX é prioridade em toda feature/fluxo novo

Não basta a função funcionar. Cada detalhe conta.

- **Rótulo sempre carrega um exemplo concreto**, via `placeholder` do próprio campo —
  nunca só o nome técnico do dado, tipo "Rótulo" ou "Valor".
- **Quando o tipo/formato de algo muda como a pessoa interage** (ex.: lista de
  escolher uma vs. marcar várias), a UI explica a diferença visivelmente, não só pelo
  nome da opção.
- **Prévia de qualquer builder** (formulário, campo, template) é **interativa de
  verdade** sempre que der — inputs reais com estado local, nunca `disabled`. Além de
  UX melhor, prévia estática esconde bug de estado que só aparece quando alguém
  realmente interage (ex.: textarea que filtrava linha vazia a cada tecla e "não
  deixava" digitar Enter — só apareceu quando a prévia virou interativa de verdade).
- Antes de dar uma tela como pronta, perguntar **"uma pessoa leiga entenderia isso
  sem explicação?"** — não só "os testes passam?".

---

## 3. Suavidade e animação são parte da entrega

**Toda tela, modal, drawer, aba, dropdown ou bloco condicional novo entra com o padrão
de movimento já adotado — nada "pipoca" na tela seco.**

**Ferramentas do projeto (usar, não reinventar):**

- `<Colapsavel aberto={bool}>` (`components/common/Transicao/`) — **padrão de
  mostrar/esconder animado** do projeto. Conteúdo fica sempre montado; anima abrir
  **e** fechar (altura via `grid-template-rows: 1fr↔0fr` + fade + deslize, curva
  easeOut ~0.48s). Use quando um toggle liga/desliga uma seção (ex.: "Exigir
  inscrição", "Todos ↔ Faixa específica"). `<BlocoRecolhivel>` (disclosure com
  cabeçalho + chevron) é construído em cima dele.
- `<Transicao modo="fade|subir|escala">` para bloco que aparece e **não some** por
  toggle (resultado de filtro, item de lista, prévia). Anima só na montagem via
  `@starting-style`.
- `<Revelar>` para item que **surge montando** (`{cond && <Revelar>}` ou item novo
  numa lista) e precisa abrir com altura — mesma curva do `<Colapsavel>`. Só anima a
  entrada; a saída fica seca (use `<Colapsavel>` se precisar dos dois lados).
- `useFecharAnimado(onClose, ms)` + classe `.saindo` para a **saída** de modal/drawer
  — a saída roda em `@keyframes` + classe, funciona em qualquer navegador (o
  `@starting-style` da entrada não pega no iOS Safari < 17.4, então saída nunca
  depende dele).
- Modal em `createPortal(document.body)` quando renderizado dentro de outro
  `<form>` (senão o submit borbulha pela árvore React e dispara o form de fora —
  corrigido com `e.stopPropagation()` no `onSubmit` do modal).
- **Micro-feedback de toque:** `:active { transform: scale(0.9x) }` com
  `transition` curta (~0.12s) em botões de ação; sempre com bloco
  `@media (prefers-reduced-motion: reduce)` zerando os `transform`.
- **Card clicável** usa as classes globais `.card-interativo` (+ `.card-midia` pra
  imagem com zoom parallax, `.card-seta` pra seta que desliza, `.card-cta` pro bloco
  de ação tingido) e define `--cor` com o token da área
  (`--cat-eventos|celulas|ministerios|financeiro`). Cor só em acento — glow do hover,
  selo, CTA, ícone; nunca no fundo do card ou no texto de conteúdo. Ver
  `domus/docs/specs/2026-09-08-cor-por-categoria-e-card-interativo-design.md`.
- **Card NÃO clicável** (relatório, gráfico, número) usa `.card-painel` — só padroniza
  a sombra (suave parada + leve reforço no hover). Nada de lift/escala/glow (não
  fingir que é clicável). Mantém o próprio fundo/borda/raio.

---

## 4. Mobile de verdade (Android e iOS)

Além dos padrões de layout acima:

- Modal/drawer vira **bottom-sheet** no breakpoint mobile
  (`@media (max-width: 767px)`): cola no rodapé, cantos de baixo retos, `.grabber`,
  `deslizarCima`/`deslizarBaixo`, `env(safe-area-inset-bottom)`, botões do rodapé
  empilham full-width.
- `100dvh`/`dvh` para altura (não `vh`).
- `-webkit-backdrop-filter` sempre junto de `backdrop-filter`.
- Trava de scroll do fundo (`document.body.style.overflow = 'hidden'`) enquanto o
  modal está aberto.
- Testar no viewport de iPhone **e** de Android antes de dar como pronto.
- Testar no navegador WebKit (Safari iOS simulado) — a animação de saída de modal
  depende de `@keyframes` + classe porque `@starting-style` não pega no iOS Safari
  < 17.4. Sem teste em webkit, isso quebra silencioso em iPhone real.

---

## 5. Cores por categoria (princípio SOLID aplicado a visual)

A regra é uma só:

- **Pergunte pela capacidade de área, não pela identidade visual específica.**
  Use o token `--cat-eventos|celulas|ministerios|financeiro` como `--cor` no
  `.card-interativo`. Cor só em acento.
- **Não** ternário `cor === 'evento' ? '#xxx' : ...`. Se a cor mudar, a UI não quebra.

Ver especificação completa em
`domus/docs/specs/2026-09-08-cor-por-categoria-e-card-interativo-design.md`.

---

## 6. Princípios SOLID aplicados a UI (paralelo ao design)

Mesmo princípio do back: **mudança localizada**. Se alterar uma decisão UI exige
editar N componentes, o desenho está errado.

- **Token CSS, não literal.** Toda cor/spacing/tipografia/token de animação vive em
  `frontend/src/styles/tokens.css` (ou similar). Mudou a paleta da marca? Um arquivo.
- **Componente composto, não colado.** `<ModalFormulario>` que renderiza header + body
  + footer + ação. Quem usa não passa `className` puro; quem usa **monta**.
- **Variant prop, não boolean plaustível.** `<Botao variant="primario|secundario|perigo">`
  em vez de `<Botao primario secondary perigo>`.
- **Sem magic numbers.** `border-radius: 12px` solto é dívida técnica. `var(--radius-card)`
  é a regra.

---

## 7. Onde achar mais

- Detalhes de stack Next.js (CSS Modules, TanStack Query, RHF+Zod):
  `frontend/CLAUDE.md`.
- Princípios SOLID aplicados a permissões: `domus/CLAUDE.md` (seção "Design — programar
  para interface").
- Spec da cor por categoria e do card-interativo:
  `domus/docs/specs/2026-09-08-cor-por-categoria-e-card-interativo-design.md`.
