# Cor por categoria + padrão de card interativo

**Data:** 2026-09-08
**Tipo:** frontend, só apresentação (sem backend, sem mudança de dados)

## Objetivo

Dar personalidade visual ao Domus — hoje mono-azul e "cru" — com **cor por área**
usada só em acentos, e elevar os cards ao nível moderno: no desktop o card sobe +
escala, a imagem faz zoom parallax, um glow na cor da categoria, e a seta do CTA
desliza; no mobile (sem hover) o design em repouso já carrega a cor e o toque dá o
feedback de escala. Isso vira o **padrão do projeto** — toda tela/card novo segue.

## Não-objetivos

- Não introduzir Tailwind, shadcn, ou `/components/ui`. O Domus é CSS Modules +
  `globals.css`; o padrão vive nesse sistema.
- Não pintar fundos/textos de cor. A cor aparece só em: glow do hover, selo de
  categoria, CTA tingido, ícone de fallback, borda no hover.
- Não mexer em listas de linha (pessoas, movimentações, inscritos, categorias
  financeiras, arquivados) — o padrão é pra **card**, não pra linha de tabela.

## Sistema de cor por categoria

Em `src/styles/globals.css`, no `:root`, quatro tokens como **triplet HSL** (pra
usar `hsl(var(--x) / opacidade)`):

```css
--cat-eventos: 221 83% 53%;      /* = a primary atual, eventos e afins */
--cat-celulas: 158 64% 40%;      /* verde */
--cat-ministerios: 262 60% 55%;  /* roxo/violeta */
--cat-financeiro: 38 92% 50%;    /* âmbar */
```

> **Verificar antes:** se `--color-primary` já é definido como HSL triplet em
> algum lugar, reusar; senão `--cat-eventos` é a fonte da verdade pro azul dos
> cards e o valor deve casar com o `--color-primary` hex atual (conferir
> visualmente). Pessoas/Visitantes **não têm cor** — é a base neutra.

Cada card define uma variável local `--cor` apontando pro token da sua área, e
todos os acentos usam `hsl(var(--cor) / …)`. Ex.: `EventoCard` → `--cor: var(--cat-eventos)`.

## Padrão de card interativo (classes globais)

Bloco novo em `src/styles/globals.css` (sem arquivo separado, sem `@import` —
tudo já vive no globals). Classes globais que os componentes adicionam **junto**
das classes de módulo: `className={\`${styles.card} card-interativo\`}`.

### `.card-interativo`

O container clicável. Lê `--cor` (o componente define; default = `--cat-eventos`).

```css
.card-interativo {
  --cor: var(--cat-eventos);
  position: relative;
  transition:
    transform 0.38s cubic-bezier(0.22, 1, 0.36, 1),
    box-shadow 0.38s cubic-bezier(0.22, 1, 0.36, 1),
    border-color 0.3s;
}
@media (hover: hover) {
  .card-interativo:hover {
    transform: translateY(-6px) scale(1.03);
    border-color: hsl(var(--cor) / 0.35);
    box-shadow:
      0 0 0 1px hsl(var(--cor) / 0.15),
      0 26px 50px -18px hsl(var(--cor) / 0.45);
    z-index: 2;
  }
}
/* toque: feedback de escala (vale mobile e desktop) */
.card-interativo:active {
  transform: translateY(-2px) scale(0.995);
}
@media (prefers-reduced-motion: reduce) {
  .card-interativo { transition: none; }
  .card-interativo:hover,
  .card-interativo:active { transform: none; }
}
```

### `.card-midia` — imagem com zoom parallax

Aplicada no elemento de imagem (o `<img>` ou o `<div background-image>`). Zoom só
no hover do `.card-interativo` ancestral.

```css
.card-midia {
  transition: transform 0.5s cubic-bezier(0.22, 1, 0.36, 1);
}
@media (hover: hover) {
  .card-interativo:hover .card-midia { transform: scale(1.1); }
}
@media (prefers-reduced-motion: reduce) {
  .card-midia { transition: none; }
  .card-interativo:hover .card-midia { transform: none; }
}
```

> O container da imagem precisa de `overflow: hidden` (já é o caso no
> `EventoCard.module.css` `.imagem`; garantir nos demais).

### `.card-seta` — seta do CTA que desliza

Aplicada no ícone de seta dentro de um CTA.

```css
.card-seta {
  transition: transform 0.3s cubic-bezier(0.22, 1, 0.36, 1);
}
@media (hover: hover) {
  .card-interativo:hover .card-seta { transform: translateX(4px); }
}
@media (prefers-reduced-motion: reduce) {
  .card-seta,
  .card-interativo:hover .card-seta { transition: none; transform: none; }
}
```

### `.card-cta` — bloco de ação tingido (opcional, onde faz sentido)

```css
.card-cta {
  background: hsl(var(--cor) / 0.08);
  border: 1px solid hsl(var(--cor) / 0.18);
  color: hsl(var(--cor));
  transition: background 0.3s, border-color 0.3s;
}
@media (hover: hover) {
  .card-interativo:hover .card-cta {
    background: hsl(var(--cor) / 0.16);
    border-color: hsl(var(--cor) / 0.3);
  }
}
```

## Onde aplica

### `EventoCard` (`src/components/module/eventos/EventoCard.tsx` + `.module.css`)

- `.card` ganha `card-interativo` no `className` + `--cor: var(--cat-eventos)`
  (via CSS no `.card` do módulo).
- `.imagemFoto` (o `<img>`) e `.imagemPlaceholder` ganham `card-midia`.
- Os `.selo*` de categoria/recorte passam a usar `hsl(var(--cor) / …)` no lugar
  das cores fixas por variante — **mas** o selo de situação (`seloVariante`:
  agendado/em andamento/encerrado) mantém suas cores semânticas próprias (verde/
  amarelo/cinza), não é "categoria". Só o `.seloRecorte` (Kids, Jovens…) e um
  eventual selo de tipo adotam `--cor`.
- **Não** adicionar CTA "Ver detalhes" — o `EventoCard` já tem `SelosInscricaoCard`
  como área de ação. O card inteiro é clicável; lift + zoom + glow bastam.
- Remover o `.card:hover { translateY(-2px) }` atual do módulo (o
  `.card-interativo:hover` assume). Manter `.cardEncerrado` (opacity 0.7).

### Grade de eventos (`src/app/(app)/eventos/(lista)/Page.module.css`)

- `.grid` desktop: `repeat(4, 1fr)` → `repeat(3, 1fr)`. Cascata: `1100px` →
  `repeat(2)`, `720px` → `1fr`. (Hoje é 4 → 3 → 2 → 1; vira 3 → 2 → 1.)
- `TAMANHO_PAGINA` em `page.tsx`: 12 → **9** (3 linhas de 3).
- Cards ficam maiores; a `.imagem` (banner) já é `aspect-ratio` — conferir que
  não distorce e que a foto `DISPLAY` (1200px) aguenta o tamanho novo.

### Cards de célula (`src/app/(app)/celulas/(lista)/page.tsx` + `page.module.css`)

- `.card` (o `<div role="button">`) ganha `card-interativo` + `--cor: var(--cat-celulas)`.
- `.cardFotoBtn` / `.cardFoto` (a foto, quando existe): a `<img>` ganha `card-midia`.
  O `.cardFotoBtn` já tem `overflow: hidden`.
- `.cardIcon` (fallback sem foto): fundo passa a `hsl(var(--cor) / 0.12)`, cor
  `hsl(var(--cor))`; ganha um leve `scale(1.08) rotate(-3deg)` no
  `.card-interativo:hover` (classe `card-midia` serve, ou uma regra própria).
- Remover o `.card:active { transform: scale(0.99) }` atual (o padrão assume).

### Cards de ministério (`src/app/(app)/ministerios/(lista)/page.tsx` + `ministerios.module.css`)

- Igual à célula, com `--cor: var(--cat-ministerios)`.

### Card de evento do início (`src/app/(app)/inicio/page.tsx` + `inicio.module.css`)

- `.cardEvento` (o `<button>`) ganha `card-interativo` + `--cor: var(--cat-eventos)`.
- Não tem imagem — sem `card-midia`. O `.dataChip`/`.iconeEvento` adotam `--cor`.
- `.eventoAcao` (já muda no hover hoje) vira `card-cta`; a seta dentro dele ganha
  `card-seta`. Se não houver `<svg>` de seta hoje, adicionar um `ArrowRight`
  `size={14}` no `.eventoAcao`.
- Remover o `.cardEvento:active { scale(0.985) }` e o `.cardEvento:hover`
  (border + shadow) atuais — o padrão assume; manter só o
  `.cardEvento:hover .eventoAcao` se ele fizer algo além do que o `card-cta` já faz.

### Fora de escopo desta entrega (mas seguem o padrão quando ganharem cards)

Financeiro (categorias/movimentações são linhas hoje), igrejas vinculadas,
dashboard. Quando qualquer um virar card, usa `card-interativo` + o token de cor
da área.

## Padrão do projeto (guardrail)

- **`backend/api/CLAUDE.md`**, seção "Suavidade e animação são parte da entrega":
  adicionar o item — *"Card clicável usa as classes globais `.card-interativo`
  (+ `.card-midia` / `.card-seta` / `.card-cta` conforme o caso) e define
  `--cor` com o token da sua área (`--cat-eventos|celulas|ministerios|financeiro`).
  Não pintar fundo/texto de cor — só acento (glow, selo, CTA, ícone)."*
- **Memória** `animacao-suavidade-padrao-front.md`: anexar o mesmo, com ponteiro
  pra este spec.

## Acessibilidade

- `prefers-reduced-motion`: cobre todas as classes (zera transform/transição).
- Contraste: os selos de categoria são texto branco sobre `hsl(var(--cor))` —
  verificar que verde/roxo/âmbar em `text-on` passam AA (o âmbar `38 92% 50%` com
  branco costuma falhar; usar `hsl(var(--cor))` **escurecido** pro selo âmbar, ex.
  `38 92% 38%`, ou texto escuro no selo âmbar). Definir no CSS do selo por área.
- O glow é decorativo (box-shadow) — sem impacto de leitor de tela.
- Foco de teclado: `.card-interativo:focus-visible` reusa o mesmo realce do hover
  (lift + glow) — adicionar essa regra junto do `:hover`.

## Arquivos

**Novos:** nenhum arquivo.

**Modificados:**
- `frontend/src/styles/globals.css` — tokens `--cat-*` + o bloco `.card-interativo` / `.card-midia` / `.card-seta` / `.card-cta`.
- `frontend/src/components/module/eventos/EventoCard.tsx` + `.module.css`
- `frontend/src/app/(app)/eventos/(lista)/Page.module.css` — grid 4→3
- `frontend/src/app/(app)/eventos/(lista)/page.tsx` — `TAMANHO_PAGINA` 12→9
- `frontend/src/app/(app)/celulas/(lista)/page.tsx` + `page.module.css`
- `frontend/src/app/(app)/ministerios/(lista)/page.tsx` + `ministerios.module.css`
- `frontend/src/app/(app)/inicio/page.tsx` + `inicio.module.css`
- `backend/api/CLAUDE.md` — guardrail

**Removidos:** nenhum.

## Teste

Sem testes de frontend. Validação manual:

- Eventos: grade em 3 colunas, cards maiores, foto boa. Hover → sobe + escala,
  zoom parallax na foto, glow azul, sem distorção. Card encerrado com opacity.
- Células / ministérios: hover → lift + glow verde / roxo, ícone de fallback com
  a cor certa e o leve giro.
- Início: `.cardEvento` com lift + glow azul, seta do CTA desliza.
- Selos de categoria (Kids, Jovens…) na cor da área; selo de situação
  (agendado/encerrado) **mantém** verde/amarelo/cinza semânticos.
- Mobile (iPhone + Android): 1 coluna em eventos, sem zoom/glow forte, toque dá
  escala; a cor já aparece parada (selo, CTA). Sem overflow horizontal.
- `prefers-reduced-motion`: sem transform, tudo funcional.
- Teclado: `Tab` até um card → realce igual ao hover; `Enter` abre.
- Contraste dos selos verde/roxo/âmbar com o texto — legível.
