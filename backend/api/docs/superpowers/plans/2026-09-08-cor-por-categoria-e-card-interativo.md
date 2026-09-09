# Cor por Categoria + Padrão de Card Interativo — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dar cor por área ao Domus (só em acento) e um padrão de card interativo moderno — hover com lift/escala/zoom-parallax/glow, toque com escala — aplicado nos cards de eventos, células, ministérios e início, e codificado como padrão do projeto.

**Architecture:** Tokens `--cat-*` (HSL triplet) em `src/styles/tokens.css` + classes globais `.card-interativo` / `.card-midia` / `.card-seta` / `.card-cta` em `src/styles/globals.css`. Os componentes adicionam as classes globais junto das classes de módulo e definem `--cor` (no CSS do módulo) com o token da sua área. Nenhum arquivo novo.

**Tech Stack:** Next.js 16, React 19, TypeScript, CSS Modules + `src/styles/globals.css` (importa `tokens.css`). Sem Tailwind, sem shadcn.

**Spec:** `backend/api/docs/superpowers/specs/2026-09-08-cor-por-categoria-e-card-interativo-design.md`

**Referência visual:** mockups em `.superpowers/brainstorm/1115022-1788905360/content/cards.html` e `cards-mobile.html` (git-ignored).

## Global Constraints

- Só frontend. Sem backend, sem mudança de dados/tipos.
- Toda animação tem `@media (prefers-reduced-motion: reduce)` que zera transform/transição.
- Cor por categoria só em **acento** (glow do hover, selo de recorte, CTA tingido, ícone de fallback, borda no hover, foco). **Nunca** em fundo de card ou texto de conteúdo.
- Selo de **situação** do evento (agendado/em andamento/encerrado) **mantém** as cores semânticas atuais (verde/amarelo/cinza) — não é categoria.
- Tokens de cor exatos (HSL triplet, pra `hsl(var(--x) / opacidade)`):
  - `--cat-eventos: 221 83% 53%` (= `--color-primary` #2563EB em HSL)
  - `--cat-celulas: 158 64% 40%`
  - `--cat-ministerios: 262 60% 55%`
  - `--cat-financeiro: 38 92% 50%`
- Pessoas/Visitantes não têm cor (base neutra).
- `hover` só dentro de `@media (hover: hover)` (no touch o `:hover` "gruda" após o tap).
- Cada task fecha com `npx tsc --noEmit`, `npx eslint <arquivos>`, `npx next build` (`✓ Compiled successfully`) e commit. Rodar de `frontend/`.
- Comentários de código em português brasileiro com acentuação.
- Commit trailers: `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>` e `Claude-Session: https://claude.ai/code/session_01PgEuCSiVzUaTXWAUfwHjg5`.

---

## File Structure

**Modificados:**
- `frontend/src/styles/tokens.css` — tokens `--cat-*`.
- `frontend/src/styles/globals.css` — bloco `.card-interativo` / `.card-midia` / `.card-seta` / `.card-cta`.
- `frontend/src/components/module/eventos/EventoCard.tsx` + `EventoCard.module.css`.
- `frontend/src/app/(app)/eventos/(lista)/Page.module.css` — grid 4→3.
- `frontend/src/app/(app)/eventos/(lista)/page.tsx` — `TAMANHO_PAGINA` 12→9.
- `frontend/src/app/(app)/celulas/(lista)/page.tsx` + `page.module.css`.
- `frontend/src/app/(app)/ministerios/(lista)/page.tsx` + `ministerios.module.css`.
- `frontend/src/app/(app)/inicio/page.tsx` + `inicio.module.css`.
- `backend/api/CLAUDE.md` — guardrail.

**Novos:** nenhum.

---

## Task 1: Tokens de cor + classes globais de card + guardrail

**Files:**
- Modify: `frontend/src/styles/tokens.css` (`:root`, após as cores principais)
- Modify: `frontend/src/styles/globals.css` (fim do arquivo)
- Modify: `backend/api/CLAUDE.md` (seção "Suavidade e animação são parte da entrega")

**Interfaces:**
- Consumes: nada.
- Produces:
  - Tokens: `--cat-eventos`, `--cat-celulas`, `--cat-ministerios`, `--cat-financeiro` (HSL triplets no `:root`).
  - Classes globais: `.card-interativo`, `.card-midia`, `.card-seta`, `.card-cta`.
    Contrato: o componente adiciona `card-interativo` ao container clicável e define
    `--cor: var(--cat-<area>)` no CSS do módulo desse container. `card-midia` vai no
    elemento de imagem (dentro de um ancestral com `overflow: hidden`). `card-seta`
    no `<svg>` de seta. `card-cta` no bloco de ação.
  Consumido pelas Tasks 2, 3, 4.

- [ ] **Step 1: Adicionar os tokens `--cat-*` em `tokens.css`**

Logo após `--color-primary-light: #EFF6FF;` no `:root`:

```css
  /* ─── Cor por categoria (acento: glow, selo, CTA, ícone) — HSL triplet
     pra usar hsl(var(--cat-x) / opacidade). Ver spec
     2026-09-08-cor-por-categoria-e-card-interativo. ─── */
  --cat-eventos: 221 83% 53%;      /* = --color-primary (#2563EB) */
  --cat-celulas: 158 64% 40%;      /* verde */
  --cat-ministerios: 262 60% 55%;  /* roxo/violeta */
  --cat-financeiro: 38 92% 50%;    /* âmbar */
```

- [ ] **Step 2: Adicionar o bloco de classes globais no fim de `globals.css`**

```css
/* ─── Card interativo: padrão de card clicável do projeto ───────────────────
   Uso: className={`${styles.card} card-interativo`} + no CSS do módulo define
   `--cor: var(--cat-<area>)` no seletor do card. Ver spec
   2026-09-08-cor-por-categoria-e-card-interativo. ───────────────────────── */
.card-interativo {
  --cor: var(--cat-eventos);
  position: relative;
  transition:
    transform 0.38s cubic-bezier(0.22, 1, 0.36, 1),
    box-shadow 0.38s cubic-bezier(0.22, 1, 0.36, 1),
    border-color 0.3s;
}
@media (hover: hover) {
  .card-interativo:hover,
  .card-interativo:focus-visible {
    transform: translateY(-6px) scale(1.03);
    border-color: hsl(var(--cor) / 0.35);
    box-shadow:
      0 0 0 1px hsl(var(--cor) / 0.15),
      0 26px 50px -18px hsl(var(--cor) / 0.45);
    z-index: 2;
    outline: none;
  }
}
.card-interativo:active {
  transform: translateY(-2px) scale(0.995);
}

.card-midia {
  transition: transform 0.5s cubic-bezier(0.22, 1, 0.36, 1);
}
@media (hover: hover) {
  .card-interativo:hover .card-midia {
    transform: scale(1.1);
  }
}

.card-seta {
  transition: transform 0.3s cubic-bezier(0.22, 1, 0.36, 1);
}
@media (hover: hover) {
  .card-interativo:hover .card-seta {
    transform: translateX(4px);
  }
}

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

@media (prefers-reduced-motion: reduce) {
  .card-interativo,
  .card-midia,
  .card-seta,
  .card-cta {
    transition: none;
  }
  .card-interativo:hover,
  .card-interativo:focus-visible,
  .card-interativo:active {
    transform: none;
  }
  .card-interativo:hover .card-midia,
  .card-interativo:hover .card-seta {
    transform: none;
  }
}
```

- [ ] **Step 3: Guardrail no `backend/api/CLAUDE.md`**

Na seção "**Suavidade e animação são parte da entrega, não enfeite opcional.**",
na lista de "Ferramentas do projeto (usar, não reinventar)", adicionar um item:

```markdown
  - **Card clicável** usa as classes globais `.card-interativo` (+ `.card-midia`
    pra imagem com zoom parallax, `.card-seta` pra seta que desliza, `.card-cta`
    pro bloco de ação tingido) e define `--cor` com o token da área
    (`--cat-eventos|celulas|ministerios|financeiro`). Cor só em acento — glow do
    hover, selo, CTA, ícone; nunca no fundo do card ou no texto. Ver
    `docs/superpowers/specs/2026-09-08-cor-por-categoria-e-card-interativo-design.md`.
```

- [ ] **Step 4: Verificar build**

Run (de `frontend/`):
```bash
npx next build 2>&1 | grep -E "Compiled|error|Failed"
```
Esperado: `✓ Compiled successfully` (CSS puro, sem consumidor ainda — só valida sintaxe).

- [ ] **Step 5: Commit**

```bash
cd /home/jos-filipe-oliveira-pereira/Documents/domus
git add frontend/src/styles/tokens.css frontend/src/styles/globals.css backend/api/CLAUDE.md
git commit -m "feat(front): tokens de cor por categoria + classes globais de card interativo

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01PgEuCSiVzUaTXWAUfwHjg5"
```

---

## Task 2: EventoCard + grade de eventos

**Files:**
- Modify: `frontend/src/components/module/eventos/EventoCard.tsx`
- Modify: `frontend/src/components/module/eventos/EventoCard.module.css`
- Modify: `frontend/src/app/(app)/eventos/(lista)/Page.module.css`
- Modify: `frontend/src/app/(app)/eventos/(lista)/page.tsx`

**Interfaces:**
- Consumes: `.card-interativo`, `.card-midia` (Task 1); token `--cat-eventos`.
- Produces: nada (folha).

- [ ] **Step 1: `EventoCard.tsx` — adicionar classes globais**

No `<article>` (hoje `className={\`${styles.card} ${...cardEncerrado}\`}`):

```tsx
<article
  className={`${styles.card} card-interativo ${evento.situacao === 'ENCERRADO' ? styles.cardEncerrado : ''}`}
  onClick={() => onAbrirDetalhe(evento)}
>
```

Na imagem — o `<img className={styles.imagemFoto}>` e o `<div className={styles.imagemPlaceholder}>`:

```tsx
{urlFoto(evento.fotoId, 'DISPLAY') ? (
  // eslint-disable-next-line @next/next/no-img-element -- servida por /api/fotos
  <img src={urlFoto(evento.fotoId, 'DISPLAY')!} alt={evento.titulo} className={`${styles.imagemFoto} card-midia`} />
) : (
  <div className={`${styles.imagemPlaceholder} card-midia`}>
    <CalendarDays size={32} />
  </div>
)}
```

> Conferir se já existe `// eslint-disable-next-line @next/next/no-img-element` acima do `<img>` — se sim, manter; o comentário acima é ilustrativo.

- [ ] **Step 2: `EventoCard.module.css` — `--cor`, remover hover antigo, ajustar selo de recorte**

Substituir o bloco `.card` + hover + active atuais por:

```css
.card {
  --cor: var(--cat-eventos);
  display: flex;
  flex-direction: column;
  background: var(--color-bg-white);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  overflow: hidden;
  cursor: pointer;
}
.cardEncerrado {
  opacity: 0.7;
}
```

Remover: o `@media (prefers-reduced-motion)` que fala de `.card` (o de `globals.css`
assume), o `@media (hover: hover) { .card:hover {...} }` e o `.card:active`.

`.seloRecorte` (linha ~84) — trocar as cores fixas por acento da categoria:

```css
.seloRecorte {
  background: hsl(var(--cor) / 0.14);
  color: hsl(var(--cor));
}
```

Não mexer em `.statusEmBreve` / `.statusHoje` / `.statusEncerrado` (cores semânticas de situação).

`.imagem` — garantir `overflow: hidden` (o `card-midia` escala além): a `.imagem` tem `position: relative` e `height: 140px`; adicionar `overflow: hidden;`.

- [ ] **Step 3: `Page.module.css` (eventos) — grade 4 → 3**

Bloco `.grid` (linha ~146) e media queries (linhas ~203/206/209):

```css
.grid {
  display: grid;
  gap: 22px;
  grid-template-columns: repeat(3, 1fr);
}
@media (max-width: 1100px) {
  .grid { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 700px) {
  .grid { grid-template-columns: 1fr; }
}
```

> Ler o arquivo real primeiro — o `gap` atual pode ser outro; ajustar só o
> `grid-template-columns` e os breakpoints, preservar o resto do `.grid`.

- [ ] **Step 4: `page.tsx` (eventos) — `TAMANHO_PAGINA` 12 → 9**

```tsx
const TAMANHO_PAGINA = 9
```

- [ ] **Step 5: Tipos, lint, build**

Run (de `frontend/`):
```bash
npx tsc --noEmit && npx eslint "src/components/module/eventos/EventoCard.tsx" "src/app/(app)/eventos/(lista)/page.tsx" && npx next build 2>&1 | grep -E "Compiled|error|Failed"
```
Esperado: limpo, `✓ Compiled successfully`.

- [ ] **Step 6: Teste manual (`npm run dev`)**

- [ ] `/eventos`: grade em **3 colunas**, cards maiores, foto sem distorção.
- [ ] Hover num card: sobe + escala 1.03, foto faz **zoom** dentro do card (não vaza), **glow azul**.
- [ ] Card `ENCERRADO`: opacity 0.7 + o hover ainda funciona.
- [ ] Selo de recorte (Kids/Jovens) em azul tingido; selo de situação (agendado/encerrado) **mantém** verde/cinza.
- [ ] Mobile (DevTools): 1 coluna, sem zoom/glow, toque dá escala.
- [ ] `Tab` até um card → mesmo realce do hover; `Enter` abre o detalhe.

- [ ] **Step 7: Commit**

```bash
cd /home/jos-filipe-oliveira-pereira/Documents/domus
git add frontend/src/components/module/eventos/ "frontend/src/app/(app)/eventos/(lista)/"
git commit -m "feat(eventos): card interativo + grade 3 colunas + cor da categoria

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01PgEuCSiVzUaTXWAUfwHjg5"
```

---

## Task 3: Cards de célula e ministério

**Files:**
- Modify: `frontend/src/app/(app)/celulas/(lista)/page.tsx` + `page.module.css`
- Modify: `frontend/src/app/(app)/ministerios/(lista)/page.tsx` + `ministerios.module.css`

**Interfaces:**
- Consumes: `.card-interativo`, `.card-midia` (Task 1); tokens `--cat-celulas`, `--cat-ministerios`.
- Produces: nada.

- [ ] **Step 1: `celulas/(lista)/page.tsx` — classe global no card**

No `<div key={c.id} className={styles.card} role="button" ...>`:

```tsx
<div key={c.id} className={`${styles.card} card-interativo`}
  role="button" tabIndex={0}
  onClick={() => router.push(`/celulas/${c.id}`)}
  onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') router.push(`/celulas/${c.id}`) }}
>
```

Na foto — o `<img className={styles.cardFoto}>` dentro do `<button className={styles.cardFotoBtn}>`:

```tsx
<img src={urlFoto(c.fotoId, 'THUMB')!} alt="" className={`${styles.cardFoto} card-midia`} />
```

- [ ] **Step 2: `celulas/(lista)/page.module.css` — `--cor`, remover hover/active antigos, ícone com a cor**

No `.card`:
```css
.card {
  --cor: var(--cat-celulas);
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 28px 24px 24px;
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border);
  background: var(--color-bg-white);
  min-width: 0;
  cursor: pointer;
  position: relative;
}
```

Remover: `@media (hover: hover) { .card:hover { background } }`, `.card:active { transform }`,
e o `.card:focus-visible { outline }` (o `.card-interativo:focus-visible` assume).
Manter o bloco `@media (prefers-reduced-motion)` se ele tratar outra coisa; se só
tratava `.card`, remover.

`.cardIcon` (fallback sem foto) — trocar pra cor da categoria + leve giro no hover:
```css
.cardIcon {
  width: 64px;
  height: 64px;
  border-radius: var(--radius-lg);
  background: hsl(var(--cat-celulas) / 0.12);
  color: hsl(var(--cat-celulas));
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 4px;
  transition: transform 0.4s cubic-bezier(0.22, 1, 0.36, 1);
}
@media (hover: hover) {
  .card:hover .cardIcon { transform: scale(1.08) rotate(-3deg); }
}
@media (prefers-reduced-motion: reduce) {
  .cardIcon { transition: none; }
  .card:hover .cardIcon { transform: none; }
}
```

> Ler o `.cardIcon` real — pode já ter cor/tamanho diferentes; preservar
> dimensões, trocar só as cores e adicionar a transição/giro.

- [ ] **Step 3: `ministerios/(lista)/page.tsx` + `ministerios.module.css` — igual, com `--cat-ministerios`**

Mesmas mudanças do Step 1/2, no card de ministério:
- `<div className={\`${styles.card} card-interativo\`} ...>`
- `<img className={\`${styles.cardFoto} card-midia\`} ...>`
- `.card { --cor: var(--cat-ministerios); ... }` + remover hover/active/focus-visible antigos
- `.cardIcon { background: hsl(var(--cat-ministerios) / 0.12); color: hsl(var(--cat-ministerios)); ... }` + giro no hover + reduced-motion

> Conferir os nomes de classe reais em `ministerios.module.css` — se forem
> `.card` / `.cardFoto` / `.cardIcon` (como em células), aplicar direto; se
> diferirem, mapear.

- [ ] **Step 4: Tipos, lint, build**

Run (de `frontend/`):
```bash
npx tsc --noEmit && npx eslint "src/app/(app)/celulas/(lista)/page.tsx" "src/app/(app)/ministerios/(lista)/page.tsx" && npx next build 2>&1 | grep -E "Compiled|error|Failed"
```
Esperado: limpo, `✓ Compiled successfully`.

- [ ] **Step 5: Teste manual**

- [ ] `/celulas`: hover num card → lift + **glow verde**; card com foto → foto faz zoom; card sem foto → ícone verde com leve giro.
- [ ] `/ministerios`: idem, **glow roxo**.
- [ ] `Tab` → mesmo realce; `Enter`/`Espaço` abre.
- [ ] Mobile: toque dá escala; sem zoom/glow forte.
- [ ] `prefers-reduced-motion`: sem transform.

- [ ] **Step 6: Commit**

```bash
cd /home/jos-filipe-oliveira-pereira/Documents/domus
git add "frontend/src/app/(app)/celulas/(lista)/" "frontend/src/app/(app)/ministerios/(lista)/"
git commit -m "feat(celulas,ministerios): card interativo + cor da categoria (verde/roxo)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01PgEuCSiVzUaTXWAUfwHjg5"
```

---

## Task 4: Card de evento do início

**Files:**
- Modify: `frontend/src/app/(app)/inicio/page.tsx`
- Modify: `frontend/src/app/(app)/inicio/inicio.module.css`

**Interfaces:**
- Consumes: `.card-interativo`, `.card-cta`, `.card-seta` (Task 1); token `--cat-eventos`.
- Produces: nada.

- [ ] **Step 1: `inicio/page.tsx` — classe global + seta no CTA**

No `<button key={e.id} className={styles.cardEvento} onClick={...}>`:
```tsx
<button key={e.id} className={`${styles.cardEvento} card-interativo`} onClick={() => setEventoAberto(e.id)}>
```

No `.eventoAcao` (bloco de ação no rodapé do card) — adicionar `card-cta` na classe
e uma seta `ArrowRight` com `card-seta`. Ler o JSX real do `.eventoAcao`; se hoje
tem texto tipo "Ver detalhes" sem ícone:
```tsx
<span className={`${styles.eventoAcao} card-cta`}>
  Ver detalhes
  <ArrowRight size={14} className="card-seta" />
</span>
```
Importar `ArrowRight` de `lucide-react` (verificar se já está importado no arquivo).

- [ ] **Step 2: `inicio.module.css` — `--cor`, remover hover/active antigos do cardEvento**

No `.cardEvento`:
```css
.cardEvento {
  --cor: var(--cat-eventos);
  /* ...manter display/flex/padding/border-radius/background/border atuais... */
  border: 1px solid var(--color-border);
  cursor: pointer;
}
```
Remover: `@media (hover: hover) { .cardEvento:hover { border-color + box-shadow } }`
e `.cardEvento:active { transform: scale(0.985) }`.

O `.cardEvento:hover .eventoAcao` (linha ~118, hoje muda background pra primary):
- Se o `card-cta` já dá o efeito de fundo tingido no hover, **remover** essa regra.
- Se o efeito atual (fundo sólido primary + texto branco) for desejado, manter
  mas migrar as cores fixas pra `hsl(var(--cor) / …)`. **Decisão:** usar o
  `card-cta` (tingido leve), remover a regra antiga — fica mais coerente com os
  outros cards.

`.eventoAcao` no módulo — remover `background`/`border`/`color` fixos (o `card-cta`
global define); manter só layout (padding, border-radius, display, gap,
font-size). Manter a `transition` fora ou deixar o global cuidar.

`.dataChip` / `.iconeEvento` (linhas ~31/55) — trocar `var(--color-primary-light)` /
`var(--color-primary)` por `hsl(var(--cor) / 0.12)` / `hsl(var(--cor))` (fica
idêntico visualmente hoje, mas passa a respeitar `--cor`).

- [ ] **Step 3: Tipos, lint, build**

Run (de `frontend/`):
```bash
npx tsc --noEmit && npx eslint "src/app/(app)/inicio/page.tsx" && npx next build 2>&1 | grep -E "Compiled|error|Failed"
```
Esperado: limpo, `✓ Compiled successfully`.

- [ ] **Step 4: Teste manual**

- [ ] `/inicio`: card de próximo evento com lift + **glow azul** no hover; o CTA
  "Ver detalhes" tinge e a **seta desliza**.
- [ ] `Tab` → realce; `Enter` abre o resumo do evento.
- [ ] Mobile: toque dá escala; CTA já tingido em repouso.
- [ ] `prefers-reduced-motion`: sem transform.

- [ ] **Step 5: Commit**

```bash
cd /home/jos-filipe-oliveira-pereira/Documents/domus
git add "frontend/src/app/(app)/inicio/"
git commit -m "feat(inicio): card de evento interativo + CTA com seta que desliza

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01PgEuCSiVzUaTXWAUfwHjg5"
```

---

## Validação final (após as 4 tasks)

Passada única cobrindo o spec:

- [ ] Contraste: o selo de recorte é `hsl(var(--cor))` sobre `hsl(var(--cor) / 0.14)` (texto colorido sobre fundo claro da mesma cor) — legível nas 3 cores. Se o `.card-cta` do início usar texto `hsl(var(--cor))` sobre `hsl(var(--cor) / 0.08)`, conferir âmbar/roxo/verde. **Não há selo de categoria com texto branco sobre cor cheia nesta entrega** (o `.selo` do EventoCard tem fundo branco translúcido); se algum for adicionado, o âmbar precisa de `--cat-financeiro` escurecido (`38 92% 38%`) ou texto escuro.
- [ ] `grep -rn "card-interativo" frontend/src/app frontend/src/components` — 4 consumidores (EventoCard, célula, ministério, início cardEvento).
- [ ] Nenhum card ficou com fundo/texto colorido (só acento).
- [ ] `prefers-reduced-motion` global: um único teste na home cobre todos os cards.
- [ ] Mobile (iPhone + Android viewport): eventos 1 coluna, sem overflow horizontal, toque responsivo em todos.

---

## Self-Review

**1. Spec coverage:**
- Sistema de cor por categoria (tokens `--cat-*`) → Task 1 Step 1. ✅
- Classes globais `.card-interativo` / `.card-midia` / `.card-seta` / `.card-cta` → Task 1 Step 2. ✅
- EventoCard: classes + `--cor` + selo de recorte + `overflow: hidden` na imagem → Task 2 Steps 1-2. ✅
- Grade eventos 4→3, `TAMANHO_PAGINA` 12→9 → Task 2 Steps 3-4. ✅
- Selo de situação mantém cores semânticas → Task 2 Step 2 (explícito) + Global Constraints. ✅
- Cards de célula/ministério + `.cardIcon` com cor + giro → Task 3. ✅
- Início `.cardEvento` + `card-cta` + `card-seta` → Task 4. ✅
- Guardrail no CLAUDE.md → Task 1 Step 3. ✅
- Acessibilidade: `prefers-reduced-motion` (Task 1 Step 2 + cada task), `:focus-visible` = realce do hover (Task 1 Step 2), contraste (Validação final). ✅
- Mobile: `@media (hover: hover)` isola o hover; `:active` vale mobile; testes de mobile em cada task. ✅
- Sem backend, sem arquivo novo → Global Constraints + File Structure. ✅
- Fora de escopo (financeiro/igrejas vinculadas/dashboard) → não há task, correto. ✅

**Gap:** a spec cita atualizar a **memória** `animacao-suavidade-padrao-front.md`. Isso não é código do repo (é `~/.claude/.../memory/`), então não entra como task do plano — o executor/controlador atualiza a memória ao concluir, fora do fluxo de commit. Anotado aqui pra não esquecer.

**2. Placeholder scan:** As notas "ler o arquivo real primeiro" são instruções de
precaução (os anchors podem ter drift), não TODOs vagos — cada uma diz exatamente
o que preservar e o que trocar. Sem "adicione error handling" solto. OK.

**3. Type consistency:**
- Tokens: `--cat-eventos|celulas|ministerios|financeiro` — mesmos nomes em Task 1 (define) e Tasks 2/3/4 (usam). ✅
- Classes: `card-interativo` / `card-midia` / `card-seta` / `card-cta` — idênticas em todas as tasks. ✅
- `--cor` — variável local setada no `.card`/`.cardEvento` de cada módulo, lida pelas classes globais. Consistente. ✅
- `TAMANHO_PAGINA` — nome real conferido no `page.tsx` de eventos (valor atual 12). ✅
