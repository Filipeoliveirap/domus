# Feedback de navegação global + biblioteca de loaders + polimento do sidebar — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dar feedback visual consistente de "carregando" em toda navegação do app, unificar os spinners numa biblioteca `<Loader>`, e polir animação/usabilidade do sidebar.

**Architecture:** (A) Um `<Loader>` em CSS Modules com 12 variantes adaptadas de um set Tailwind de referência. (B) Um provider `<NavProgress>` montado uma vez no layout que embrulha `history.pushState`/`replaceState` pra detectar toda navegação sem tocar em páginas; renderiza barra de topo OU overlay conforme uma constante. (C) Sidebar ganha pílula ativa deslizante, arrastar-pra-fechar no drawer, trava de scroll do body e stagger nos submenus.

**Tech Stack:** Next.js 16 (App Router), React 19, TypeScript, CSS Modules, Zustand (`uiStore`), `lucide-react`. Sem dependência nova.

**Spec:** `backend/api/docs/superpowers/specs/2026-08-28-feedback-navegacao-e-loaders-design.md`

> **Revisão 2026-08-28 (durante execução, após o autor testar a Peça B):** separados dois
> problemas. (1) Navegação de rota nunca bloqueia — `MODO_INDICADOR_NAV` passa a ser
> `'barra' | 'barra-e-link'` (default `'barra-e-link'`); o modo `overlay` sai. (2) Overlay
> vira componente genérico `<OverlayCarregando>` (ex-`OverlayNav`), em
> `components/common/OverlayCarregando/`, para operação bloqueante. Task 4 usa a union nova;
> Task 5 não renderiza mais overlay; a Task 5 Step 3 (`OverlayNav`) migra pra **Task 6B**;
> **Task 6C** aplica o overlay. Aplicação: reiniciar do checkout (NÃO o botão pagar — a
> animação nativa do MP fica), `ModalConfirmacaoCritica`, `ModalExcluirIgreja`, e submit
> dos forms de pessoa/evento/movimentação.

## Global Constraints

- **Sem dependência nova.** Nada de `npm install`. Barra de progresso é código nosso.
- **Sem mudança de backend.** Só `frontend/`.
- **CSS Modules + tokens.** Cores sempre via `var(--color-*)` de `src/styles/tokens.css`. Nada de Tailwind, nada de shadcn, nada de valor de cor hardcoded fora de um token.
- **Sem infra de teste de frontend** (sem Jest/Vitest/Playwright — dívida conhecida). Validação de cada task: `npx tsc --noEmit` limpo, `npx eslint <arquivos>` sem erro novo, e checklist manual. O baseline de eslint tem ~17 erros pré-existentes: comparar com `git stash` se aparecer erro, pra confirmar que não é novo.
- **Não commitar antes do autor testar.** Cada task termina com o build/lint verde; o `git commit` da task só acontece depois que o autor validar o pedaço no navegador. As tasks estão agrupadas em 3 pedaços (A: 1-3, B: 4-6, C: 7-10) com checkpoint de teste do autor ao fim de cada pedaço.
- **`prefers-reduced-motion`:** toda animação nova respeita — desacelera ou corta.
- **Responsividade:** validar viewport mobile (Chrome DevTools 390×844) além do desktop.
- Trabalhar em branch (não `main`/`develop` direto). Branch sugerida: `feat/feedback-navegacao-loaders`.
- Comandos rodam de `frontend/`.
- Mensagens de commit em português, sem `Co-Authored-By`.

---

## File Structure

**Novos:**

| Arquivo | Responsabilidade |
|---|---|
| `src/components/common/Loader/Loader.tsx` | Componente `<Loader>` + 12 exports de variante |
| `src/components/common/Loader/Loader.module.css` | Estilos e keyframes das 12 variantes |
| `src/app/demo/loaders/page.tsx` | Rota dev-only: galeria de loaders + simulador de indicador de nav |
| `src/app/demo/loaders/page.module.css` | Estilos da rota de demo |
| `src/config/navIndicator.ts` | Constante `MODO_INDICADOR_NAV` |
| `src/components/layout/NavProgress/NavProgress.tsx` | Provider: patch de history + orquestra visibilidade + escolhe render |
| `src/components/layout/NavProgress/BarraProgresso.tsx` | Barra fina de progresso no topo |
| `src/components/layout/NavProgress/OverlayNav.tsx` | Overlay com spinner central |
| `src/components/layout/NavProgress/NavProgress.module.css` | Estilos da barra e do overlay |
| `src/hooks/useArrastarParaFechar.ts` | Hook de swipe-to-close pro drawer mobile |

**Modificados:**

| Arquivo | Mudança |
|---|---|
| `src/app/(app)/loading.tsx` + `loading.module.css` | Usar `<Loader>`, remover spinner local |
| `src/components/module/pagamento/PaymentBrickCheckout.tsx` + `.module.css` | Usar `<Loader>`, remover `.spinner`/`@keyframes girar` |
| `src/store/uiStore.ts` | Campos `navsPendentes`/`navegando` + ações |
| `src/app/(app)/layout.tsx` | Montar `<NavProgress />` |
| `src/components/layout/Sidebar.tsx` | `<IconePendente>` (modo link) + pílula ativa + trava de scroll + swipe |
| `src/components/layout/Sidebar.module.css` | Pílula ativa, feedback de toque, fade do backdrop, stagger do submenu |

---

## Task 1: Componente `<Loader>` + estilos

**Files:**
- Create: `frontend/src/components/common/Loader/Loader.tsx`
- Create: `frontend/src/components/common/Loader/Loader.module.css`

**Interfaces:**
- Consumes: nada.
- Produces:
  - `Loader(props: LoaderProps): JSX.Element` — default export nomeado `Loader`.
  - `LoaderProps` = `{ variant?: LoaderVariant; size?: 'sm'|'md'|'lg'; text?: string; className?: string }`.
  - `type LoaderVariant = 'circular'|'classic'|'pulse'|'pulse-dot'|'dots'|'typing'|'wave'|'bars'|'terminal'|'text-blink'|'text-shimmer'|'loading-dots'`.
  - Exports nomeados: `CircularLoader`, `ClassicLoader`, `PulseLoader`, `PulseDotLoader`, `DotsLoader`, `TypingLoader`, `WaveLoader`, `BarsLoader`, `TerminalLoader`, `TextBlinkLoader`, `TextShimmerLoader`, `TextDotsLoader` — cada um aceita `{ className?: string; size?: 'sm'|'md'|'lg' }`; as três de texto (`TextBlinkLoader`, `TextShimmerLoader`, `TextDotsLoader`) também aceitam `text?: string`.

- [ ] **Step 1: Criar `Loader.module.css`**

```css
/* Biblioteca de loaders — adaptada de um set Tailwind/shadcn de referência pra CSS Modules
   + tokens do Domus. CSS Modules escopa os nomes de @keyframes junto com as classes, então
   não colide com o `spin` de (app)/loading.module.css nem de Button.module.css. */

/* ---- tamanhos ---- */
.sm { width: 16px; height: 16px; }
.md { width: 20px; height: 20px; }
.lg { width: 24px; height: 24px; }

.srOnly {
  position: absolute;
  width: 1px; height: 1px;
  padding: 0; margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

/* ---- circular ---- */
.circular {
  border: 2px solid var(--color-primary);
  border-top-color: transparent;
  border-radius: var(--radius-full);
  animation: girar 0.7s linear infinite;
}

/* ---- classic (12 barrinhas girando) ---- */
.classic { position: relative; }
.classicBar {
  position: absolute;
  top: 0; left: 50%;
  background: var(--color-primary);
  border-radius: var(--radius-full);
  opacity: 0;
  animation: classicFade 1.2s linear infinite;
}

/* ---- pulse (anel pulsando) ---- */
.pulse { position: relative; }
.pulseRing {
  position: absolute;
  inset: 0;
  border: 2px solid var(--color-primary);
  border-radius: var(--radius-full);
  animation: pulseFino 1.5s ease-in-out infinite;
}

/* ---- pulse-dot ---- */
.pulseDot {
  background: var(--color-primary);
  border-radius: var(--radius-full);
  animation: pulsarPonto 1.2s ease-in-out infinite;
}
.pulseDot.sm { width: 4px; height: 4px; }
.pulseDot.md { width: 8px; height: 8px; }
.pulseDot.lg { width: 12px; height: 12px; }

/* ---- dots / typing (três bolinhas) ---- */
.dotsRow { display: inline-flex; align-items: center; gap: 4px; width: auto; height: auto; }
.dot {
  background: var(--color-primary);
  border-radius: var(--radius-full);
}
.dotsRow.sm .dot { width: 6px; height: 6px; }
.dotsRow.md .dot { width: 8px; height: 8px; }
.dotsRow.lg .dot { width: 10px; height: 10px; }
.dot.bounce { animation: quicarPonto 1.4s ease-in-out infinite; }
.typingRow.sm .dot { width: 4px; height: 4px; }
.typingRow.md .dot { width: 6px; height: 6px; }
.typingRow.lg .dot { width: 8px; height: 8px; }
.dot.typing { animation: digitando 1s infinite; }

/* ---- wave (5 barras) ---- */
.waveRow { display: inline-flex; align-items: center; gap: 2px; width: auto; height: auto; }
.waveBar {
  background: var(--color-primary);
  border-radius: var(--radius-full);
  animation: onda 1s ease-in-out infinite;
}
.waveRow.sm .waveBar { width: 2px; }
.waveRow.md .waveBar { width: 2px; }
.waveRow.lg .waveBar { width: 4px; }

/* ---- bars (3 barras) ---- */
.barsRow { display: inline-flex; height: auto; width: auto; }
.barsRow.sm { gap: 4px; }
.barsRow.md { gap: 6px; }
.barsRow.lg { gap: 8px; }
.bar {
  background: var(--color-primary);
  animation: ondaBarras 1.2s ease-in-out infinite;
}
.barsRow.sm .bar { width: 4px; height: 16px; }
.barsRow.md .bar { width: 6px; height: 20px; }
.barsRow.lg .bar { width: 8px; height: 24px; }

/* ---- terminal ---- */
.terminalRow { display: inline-flex; align-items: center; gap: 4px; width: auto; height: auto; }
.terminalPrompt { color: var(--color-primary); font-family: ui-monospace, monospace; }
.terminalCursor { background: var(--color-primary); animation: piscar 1s step-end infinite; }
.terminalRow.sm .terminalPrompt { font-size: 0.75rem; }
.terminalRow.md .terminalPrompt { font-size: 0.875rem; }
.terminalRow.lg .terminalPrompt { font-size: 1rem; }
.terminalRow.sm .terminalCursor { width: 6px; height: 12px; }
.terminalRow.md .terminalCursor { width: 8px; height: 16px; }
.terminalRow.lg .terminalCursor { width: 10px; height: 20px; }

/* ---- variantes de texto ---- */
.textBase { font-weight: 500; width: auto; height: auto; }
.textBase.sm { font-size: 0.75rem; }
.textBase.md { font-size: 0.875rem; }
.textBase.lg { font-size: 1rem; }
.textBlink { animation: textoPiscando 2s ease-in-out infinite; }
.textShimmer {
  background: linear-gradient(
    to right,
    var(--color-text-muted) 40%,
    var(--color-text-primary) 60%,
    var(--color-text-muted) 80%
  );
  background-size: 200% auto;
  background-clip: text;
  -webkit-background-clip: text;
  color: transparent;
  animation: brilhoTexto 4s infinite linear;
}
.loadingDots { display: inline-flex; align-items: center; width: auto; height: auto; }
.loadingDots .txt { color: var(--color-primary); font-weight: 500; }
.loadingDots .pts span { color: var(--color-primary); }
.loadingDots .pts span:nth-child(1) { animation: pontosCarregando 1.4s infinite 0.2s; }
.loadingDots .pts span:nth-child(2) { animation: pontosCarregando 1.4s infinite 0.4s; }
.loadingDots .pts span:nth-child(3) { animation: pontosCarregando 1.4s infinite 0.6s; }

/* ---- keyframes ---- */
@keyframes girar { to { transform: rotate(360deg); } }
@keyframes classicFade { 0% { opacity: 0; } 100% { opacity: 1; } }
@keyframes pulseFino {
  0%, 100% { transform: scale(0.95); opacity: 0.8; }
  50% { transform: scale(1.05); opacity: 0.4; }
}
@keyframes pulsarPonto {
  0%, 100% { transform: scale(1); opacity: 0.8; }
  50% { transform: scale(1.5); opacity: 1; }
}
@keyframes quicarPonto {
  0%, 100% { transform: scale(0.8); opacity: 0.5; }
  50% { transform: scale(1.2); opacity: 1; }
}
@keyframes digitando {
  0%, 100% { transform: translateY(0); opacity: 0.5; }
  50% { transform: translateY(-2px); opacity: 1; }
}
@keyframes onda {
  0%, 100% { transform: scaleY(1); }
  50% { transform: scaleY(0.6); }
}
@keyframes ondaBarras {
  0%, 100% { transform: scaleY(1); opacity: 0.5; }
  50% { transform: scaleY(0.6); opacity: 1; }
}
@keyframes piscar { 0%, 100% { opacity: 1; } 50% { opacity: 0; } }
@keyframes textoPiscando {
  0%, 100% { color: var(--color-primary); }
  50% { color: var(--color-text-muted); }
}
@keyframes brilhoTexto {
  0% { background-position: 200% 50%; }
  100% { background-position: -200% 50%; }
}
@keyframes pontosCarregando { 0%, 100% { opacity: 0; } 50% { opacity: 1; } }

/* ---- reduced motion ---- */
@media (prefers-reduced-motion: reduce) {
  .circular, .classicBar, .pulseRing, .pulseDot, .dot, .waveBar, .bar,
  .terminalCursor { animation-duration: 2s; }
  .textBlink, .textShimmer, .loadingDots .pts span { animation: none; }
  .textShimmer { color: var(--color-text-muted); }
}
```

- [ ] **Step 2: Criar `Loader.tsx`**

```tsx
'use client'

import { clsx } from 'clsx'
import styles from './Loader.module.css'

type Size = 'sm' | 'md' | 'lg'

export type LoaderVariant =
  | 'circular' | 'classic' | 'pulse' | 'pulse-dot' | 'dots' | 'typing'
  | 'wave' | 'bars' | 'terminal' | 'text-blink' | 'text-shimmer' | 'loading-dots'

export interface LoaderProps {
  variant?: LoaderVariant
  size?: Size
  text?: string
  className?: string
}

const CARREGANDO = 'Carregando'

function Sr() {
  return <span className={styles.srOnly}>{CARREGANDO}</span>
}

export function CircularLoader({ className, size = 'md' }: { className?: string; size?: Size }) {
  return (
    <span className={clsx(styles.circular, styles[size], className)} role="status">
      <Sr />
    </span>
  )
}

export function ClassicLoader({ className, size = 'md' }: { className?: string; size?: Size }) {
  const raio = size === 'sm' ? 8 : size === 'lg' ? 12 : 10
  const larg = size === 'sm' ? 1.5 : size === 'lg' ? 2.5 : 2
  const alt = size === 'sm' ? 6 : size === 'lg' ? 10 : 8
  return (
    <span className={clsx(styles.classic, styles[size], className)} role="status">
      {Array.from({ length: 12 }).map((_, i) => (
        <span
          key={i}
          className={styles.classicBar}
          style={{
            width: `${larg}px`,
            height: `${alt}px`,
            marginLeft: `${-larg / 2}px`,
            transformOrigin: `${larg / 2}px ${raio}px`,
            transform: `rotate(${i * 30}deg)`,
            animationDelay: `${i * 0.1}s`,
          }}
        />
      ))}
      <Sr />
    </span>
  )
}

export function PulseLoader({ className, size = 'md' }: { className?: string; size?: Size }) {
  return (
    <span className={clsx(styles.pulse, styles[size], className)} role="status">
      <span className={styles.pulseRing} />
      <Sr />
    </span>
  )
}

export function PulseDotLoader({ className, size = 'md' }: { className?: string; size?: Size }) {
  return (
    <span className={clsx(styles.pulseDot, styles[size], className)} role="status">
      <Sr />
    </span>
  )
}

export function DotsLoader({ className, size = 'md' }: { className?: string; size?: Size }) {
  return (
    <span className={clsx(styles.dotsRow, styles[size], className)} role="status">
      {[0, 1, 2].map((i) => (
        <span key={i} className={clsx(styles.dot, styles.bounce)} style={{ animationDelay: `${i * 160}ms` }} />
      ))}
      <Sr />
    </span>
  )
}

export function TypingLoader({ className, size = 'md' }: { className?: string; size?: Size }) {
  return (
    <span className={clsx(styles.dotsRow, styles.typingRow, styles[size], className)} role="status">
      {[0, 1, 2].map((i) => (
        <span key={i} className={clsx(styles.dot, styles.typing)} style={{ animationDelay: `${i * 250}ms` }} />
      ))}
      <Sr />
    </span>
  )
}

export function WaveLoader({ className, size = 'md' }: { className?: string; size?: Size }) {
  const alturas =
    size === 'sm' ? [6, 9, 12, 9, 6] : size === 'lg' ? [10, 15, 20, 15, 10] : [8, 12, 16, 12, 8]
  return (
    <span className={clsx(styles.waveRow, styles[size], className)} role="status">
      {alturas.map((h, i) => (
        <span key={i} className={styles.waveBar} style={{ height: `${h}px`, animationDelay: `${i * 100}ms` }} />
      ))}
      <Sr />
    </span>
  )
}

export function BarsLoader({ className, size = 'md' }: { className?: string; size?: Size }) {
  return (
    <span className={clsx(styles.barsRow, styles[size], className)} role="status">
      {[0, 1, 2].map((i) => (
        <span key={i} className={styles.bar} style={{ animationDelay: `${i * 0.2}s` }} />
      ))}
      <Sr />
    </span>
  )
}

export function TerminalLoader({ className, size = 'md' }: { className?: string; size?: Size }) {
  return (
    <span className={clsx(styles.terminalRow, styles[size], className)} role="status">
      <span className={styles.terminalPrompt}>{'>'}</span>
      <span className={styles.terminalCursor} />
      <Sr />
    </span>
  )
}

export function TextBlinkLoader({ text = 'Pensando', className, size = 'md' }: { text?: string; className?: string; size?: Size }) {
  return (
    <span className={clsx(styles.textBase, styles.textBlink, styles[size], className)} role="status" aria-live="polite">
      {text}
    </span>
  )
}

export function TextShimmerLoader({ text = 'Pensando', className, size = 'md' }: { text?: string; className?: string; size?: Size }) {
  return (
    <span className={clsx(styles.textBase, styles.textShimmer, styles[size], className)} role="status" aria-live="polite">
      {text}
    </span>
  )
}

export function TextDotsLoader({ text = 'Carregando', className, size = 'md' }: { text?: string; className?: string; size?: Size }) {
  return (
    <span className={clsx(styles.loadingDots, styles[size], className)} role="status" aria-live="polite">
      <span className={clsx(styles.textBase, styles[size], styles.txt)}>{text}</span>
      <span className={styles.pts}>
        <span>.</span><span>.</span><span>.</span>
      </span>
    </span>
  )
}

export function Loader({ variant = 'circular', size = 'md', text, className }: LoaderProps) {
  switch (variant) {
    case 'classic': return <ClassicLoader size={size} className={className} />
    case 'pulse': return <PulseLoader size={size} className={className} />
    case 'pulse-dot': return <PulseDotLoader size={size} className={className} />
    case 'dots': return <DotsLoader size={size} className={className} />
    case 'typing': return <TypingLoader size={size} className={className} />
    case 'wave': return <WaveLoader size={size} className={className} />
    case 'bars': return <BarsLoader size={size} className={className} />
    case 'terminal': return <TerminalLoader size={size} className={className} />
    case 'text-blink': return <TextBlinkLoader text={text} size={size} className={className} />
    case 'text-shimmer': return <TextShimmerLoader text={text} size={size} className={className} />
    case 'loading-dots': return <TextDotsLoader text={text} size={size} className={className} />
    case 'circular':
    default: return <CircularLoader size={size} className={className} />
  }
}
```

- [ ] **Step 3: Verificar tipos e lint**

Run (de `frontend/`): `npx tsc --noEmit && npx eslint src/components/common/Loader/`
Expected: sem erro. (`clsx` já é dependência do projeto.)

- [ ] **Step 4: Commit** (só após o checkpoint do pedaço A — ver Task 3)

```bash
git add frontend/src/components/common/Loader/
git commit -m "feat(front): biblioteca de loaders em CSS Modules com 12 variantes"
```

---

## Task 2: Rota de demonstração — galeria de loaders

**Files:**
- Create: `frontend/src/app/demo/loaders/page.tsx`
- Create: `frontend/src/app/demo/loaders/page.module.css`

**Interfaces:**
- Consumes: `Loader`, `LoaderVariant` de `@/components/common/Loader/Loader`.
- Produces: rota `GET /demo/loaders` (dev-only). Nenhum export consumido por outra task nesta fase; a Task 5 volta neste arquivo pra adicionar o simulador.

- [ ] **Step 1: Criar `page.module.css`**

```css
.wrapper {
  max-width: 900px;
  margin: 0 auto;
  padding: 2rem 1rem 4rem;
}
.titulo { font-size: 1.25rem; font-weight: 700; color: var(--color-text-primary); margin-bottom: 0.25rem; }
.aviso { font-size: 0.8125rem; color: var(--color-text-muted); margin-bottom: 1.5rem; }
.toggle { display: inline-flex; gap: 0.25rem; margin-bottom: 1.5rem; }
.toggle button {
  padding: 0.375rem 0.75rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  font-size: 0.8125rem;
  color: var(--color-text-muted);
  background: var(--color-bg-white);
}
.toggle button[data-ativo='true'] {
  border-color: var(--color-primary);
  color: var(--color-primary);
  background: var(--color-primary-light);
}
.grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1rem;
}
@media (min-width: 640px) { .grid { grid-template-columns: repeat(3, 1fr); } }
@media (min-width: 900px) { .grid { grid-template-columns: repeat(4, 1fr); } }
.celula {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
  min-height: 120px;
  padding: 1rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-bg-white);
}
.nome { font-size: 0.75rem; color: var(--color-text-muted); }
.secao { margin-top: 2.5rem; }
.secaoTitulo { font-size: 1rem; font-weight: 700; color: var(--color-text-primary); margin-bottom: 1rem; }
.simulador { display: flex; flex-wrap: wrap; gap: 0.75rem; }
.simulador button {
  padding: 0.5rem 1rem;
  border: 1px solid var(--color-primary);
  border-radius: var(--radius-md);
  color: var(--color-primary);
  background: var(--color-bg-white);
  font-size: 0.875rem;
}
```

- [ ] **Step 2: Criar `page.tsx` (só a galeria por enquanto)**

```tsx
'use client'

import { useState } from 'react'
import { notFound } from 'next/navigation'
import { Loader, type LoaderVariant } from '@/components/common/Loader/Loader'
import styles from './page.module.css'

const VARIANTES: LoaderVariant[] = [
  'circular', 'classic', 'pulse', 'pulse-dot', 'dots', 'typing',
  'wave', 'bars', 'terminal', 'text-blink', 'text-shimmer', 'loading-dots',
]

const TAMANHOS = ['sm', 'md', 'lg'] as const

export default function DemoLoadersPage() {
  if (process.env.NODE_ENV === 'production') notFound()

  const [tamanho, setTamanho] = useState<(typeof TAMANHOS)[number]>('md')

  return (
    <div className={styles.wrapper}>
      <h1 className={styles.titulo}>Loaders</h1>
      <p className={styles.aviso}>Rota de teste (dev-only). Escolha quais variantes ficam.</p>

      <div className={styles.toggle}>
        {TAMANHOS.map((t) => (
          <button key={t} data-ativo={tamanho === t} onClick={() => setTamanho(t)}>
            {t}
          </button>
        ))}
      </div>

      <div className={styles.grid}>
        {VARIANTES.map((v) => (
          <div key={v} className={styles.celula}>
            <Loader variant={v} size={tamanho} text="Carregando" />
            <span className={styles.nome}>{v}</span>
          </div>
        ))}
      </div>
    </div>
  )
}
```

- [ ] **Step 3: Verificar**

Run (de `frontend/`): `npx tsc --noEmit && npx eslint src/app/demo/`
Then: `npm run dev` e abrir `http://localhost:3000/demo/loaders` — as 12 células aparecem, toggle de tamanho funciona.

- [ ] **Step 4: Commit** (após checkpoint do pedaço A)

```bash
git add frontend/src/app/demo/
git commit -m "feat(front): rota demo/loaders com galeria das variantes"
```

---

## Task 3: Trocar os spinners ad-hoc pelo `<Loader>`

**Files:**
- Modify: `frontend/src/app/(app)/loading.tsx`
- Modify: `frontend/src/app/(app)/loading.module.css`
- Modify: `frontend/src/components/module/pagamento/PaymentBrickCheckout.tsx`
- Modify: `frontend/src/components/module/pagamento/PaymentBrickCheckout.module.css`

**Interfaces:**
- Consumes: `Loader` de `@/components/common/Loader/Loader`.
- Produces: nada novo.

- [ ] **Step 1: `(app)/loading.tsx`**

```tsx
import { Loader } from '@/components/common/Loader/Loader'
import styles from './loading.module.css'

export default function AppLoading() {
  return (
    <div className={styles.wrapper}>
      <Loader variant="circular" size="lg" />
    </div>
  )
}
```

- [ ] **Step 2: `(app)/loading.module.css` — remover `.spinner` e `@keyframes spin`**

Deixar só:

```css
.wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
}
```

- [ ] **Step 3: `PaymentBrickCheckout.tsx` — trocar o spinner**

No import, adicionar: `import { Loader } from '@/components/common/Loader/Loader'`

Trocar o bloco:

```tsx
          <div className={styles.carregando} role="status" aria-live="polite">
            <span className={styles.spinner} aria-hidden="true" />
            <p>
```

por:

```tsx
          <div className={styles.carregando} role="status" aria-live="polite">
            <Loader variant="circular" size="lg" />
            <p>
```

- [ ] **Step 4: `PaymentBrickCheckout.module.css` — remover `.spinner` e `@keyframes girar`**

Remover as regras `.spinner`, `@keyframes girar` e o bloco `@media (prefers-reduced-motion: reduce) { .spinner { ... } }`. Manter `.brickArea`, `.carregando`, `.processando`, `.wrapper`.

- [ ] **Step 5: Verificar**

Run (de `frontend/`): `npx tsc --noEmit && npx eslint "src/app/(app)/loading.tsx" src/components/module/pagamento/ && npm run build`
Expected: build "✓ Compiled successfully", zero erro novo de lint.

- [ ] **Step 6: CHECKPOINT — pedaço A**

Avisar o autor: *"Pedaço A pronto — biblioteca de loaders, rota `/demo/loaders`, e os spinners de `(app)/loading` e do checkout do Mercado Pago agora usam o `<Loader>`. Testa: abre `/demo/loaders`, olha as 12; confirma que o checkout e o loading de página ainda mostram spinner."*

Esperar o "ok". Só então rodar os commits das Tasks 1, 2 e 3 (podem ser 3 commits ou 1 commit coeso do pedaço A — preferência: 1 commit).

```bash
git add frontend/src/components/common/Loader/ frontend/src/app/demo/ "frontend/src/app/(app)/loading.tsx" "frontend/src/app/(app)/loading.module.css" frontend/src/components/module/pagamento/PaymentBrickCheckout.tsx frontend/src/components/module/pagamento/PaymentBrickCheckout.module.css
git commit -m "feat(front): biblioteca de loaders unica + rota demo, substitui spinners ad-hoc"
```

- [ ] **Step 7: Atualizar graphify**

Run (de `frontend/`): `graphify update .`

---

## Task 4: Estado de navegação no `uiStore` + constante de modo

**Files:**
- Modify: `frontend/src/store/uiStore.ts`
- Create: `frontend/src/config/navIndicator.ts`

**Interfaces:**
- Consumes: nada.
- Produces:
  - `useUiStore` ganha: `navsPendentes: number`, `navegando: boolean`, `iniciarNav(): void`, `finalizarNav(): void`, `resetarNav(): void`.
  - `navegando` é mantido em sincronia: `true` sse `navsPendentes > 0`.
  - `MODO_INDICADOR_NAV: 'barra' | 'overlay' | 'barra-e-link'` exportado de `@/config/navIndicator`.

- [ ] **Step 1: `config/navIndicator.ts`**

```ts
// Modo do indicador de navegação global. Trocar aqui, subir, testar — sem reescrever nada.
//   'barra'         → barra fina de progresso no topo da tela
//   'overlay'       → véu semitransparente + spinner central (bloqueia clique)
//   'barra-e-link'  → barra no topo + item do sidebar clicado troca o ícone por spinner
export const MODO_INDICADOR_NAV: 'barra' | 'overlay' | 'barra-e-link' = 'barra'
```

- [ ] **Step 2: `uiStore.ts` — adicionar estado**

```ts
import { create } from 'zustand'

// Estado de UI global e efêmero (não persiste).
interface UiState {
  navAberta: boolean
  abrirNav: () => void
  fecharNav: () => void
  alternarNav: () => void

  // Navegação em andamento — alimenta o <NavProgress>. Contador (não booleano) porque
  // dois cliques rápidos podem encavalar duas navegações; só zera quando todas terminam.
  navsPendentes: number
  navegando: boolean
  iniciarNav: () => void
  finalizarNav: () => void
  resetarNav: () => void
}

export const useUiStore = create<UiState>((set) => ({
  navAberta: false,
  abrirNav: () => set({ navAberta: true }),
  fecharNav: () => set({ navAberta: false }),
  alternarNav: () => set((s) => ({ navAberta: !s.navAberta })),

  navsPendentes: 0,
  navegando: false,
  iniciarNav: () =>
    set((s) => {
      const n = s.navsPendentes + 1
      return { navsPendentes: n, navegando: n > 0 }
    }),
  finalizarNav: () =>
    set((s) => {
      const n = Math.max(0, s.navsPendentes - 1)
      return { navsPendentes: n, navegando: n > 0 }
    }),
  resetarNav: () => set({ navsPendentes: 0, navegando: false }),
}))
```

- [ ] **Step 3: Verificar**

Run (de `frontend/`): `npx tsc --noEmit && npx eslint src/store/uiStore.ts src/config/`
Expected: sem erro.

- [ ] **Step 4: Commit** (após checkpoint do pedaço B — Task 6)

```bash
git add frontend/src/store/uiStore.ts frontend/src/config/navIndicator.ts
git commit -m "feat(front): estado de navegacao no uiStore + constante de modo do indicador"
```

---

## Task 5: `<NavProgress>` — provider, barra e overlay

**Files:**
- Create: `frontend/src/components/layout/NavProgress/NavProgress.tsx`
- Create: `frontend/src/components/layout/NavProgress/BarraProgresso.tsx`
- Create: `frontend/src/components/layout/NavProgress/OverlayNav.tsx`
- Create: `frontend/src/components/layout/NavProgress/NavProgress.module.css`
- Modify: `frontend/src/app/(app)/layout.tsx`
- Modify: `frontend/src/app/demo/loaders/page.tsx` (adicionar o simulador)

**Interfaces:**
- Consumes: `useUiStore` (`navegando`, `iniciarNav`, `finalizarNav`, `resetarNav`), `MODO_INDICADOR_NAV`, `Loader`.
- Produces:
  - `NavProgress(): JSX.Element` — default export nomeado `NavProgress`.
  - `BarraProgresso({ ativo }: { ativo: boolean }): JSX.Element`.
  - `OverlayNav({ ativo }: { ativo: boolean }): JSX.Element`.

- [ ] **Step 1: `NavProgress.module.css`**

```css
/* ---- barra de progresso no topo ---- */
.barra {
  position: fixed;
  top: 0; left: 0; right: 0;
  height: 3px;
  z-index: 100;
  pointer-events: none;
  opacity: 0;
  transition: opacity 0.2s ease;
}
.barraVisivel { opacity: 1; }
.barraInterna {
  height: 100%;
  width: 0;
  background: var(--color-primary);
  box-shadow: 0 0 8px 1px var(--color-primary);
  transition: width 0.3s ease;
}
.faseCarregando .barraInterna { width: 85%; transition: width 8s cubic-bezier(0.1, 0.7, 0.3, 1); }
.faseFinalizando .barraInterna { width: 100%; transition: width 0.2s ease; }

/* ---- overlay ---- */
.overlay {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-bg-overlay);
  backdrop-filter: blur(2px);
  -webkit-backdrop-filter: blur(2px);
  opacity: 0;
  visibility: hidden;
  transition: opacity 0.15s ease, visibility 0.15s ease;
}
.overlayVisivel { opacity: 1; visibility: visible; }
.overlay :global(.sr-only) { color: #fff; }
.overlaySpinner { filter: brightness(0) invert(1); }

@media (prefers-reduced-motion: reduce) {
  .faseCarregando .barraInterna { width: 70%; transition: width 0.3s ease; }
}
```

- [ ] **Step 2: `BarraProgresso.tsx`**

```tsx
'use client'

import { useEffect, useRef, useState } from 'react'
import { clsx } from 'clsx'
import styles from './NavProgress.module.css'

type Fase = 'oculto' | 'carregando' | 'finalizando'

export function BarraProgresso({ ativo }: { ativo: boolean }) {
  const [fase, setFase] = useState<Fase>('oculto')
  const timeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  useEffect(() => {
    if (ativo) {
      if (timeoutRef.current) clearTimeout(timeoutRef.current)
      setFase('carregando')
      return
    }
    // ativo virou false: completa a barra e some
    setFase((atual) => (atual === 'oculto' ? 'oculto' : 'finalizando'))
    timeoutRef.current = setTimeout(() => setFase('oculto'), 300)
    return () => {
      if (timeoutRef.current) clearTimeout(timeoutRef.current)
    }
  }, [ativo])

  return (
    <div
      className={clsx(
        styles.barra,
        fase !== 'oculto' && styles.barraVisivel,
        fase === 'carregando' && styles.faseCarregando,
        fase === 'finalizando' && styles.faseFinalizando,
      )}
      aria-hidden="true"
    >
      <div className={styles.barraInterna} />
    </div>
  )
}
```

- [ ] **Step 3: `OverlayNav.tsx`**

```tsx
'use client'

import { clsx } from 'clsx'
import { Loader } from '@/components/common/Loader/Loader'
import styles from './NavProgress.module.css'

export function OverlayNav({ ativo }: { ativo: boolean }) {
  return (
    <div className={clsx(styles.overlay, ativo && styles.overlayVisivel)} role="status" aria-live="polite">
      <span className={styles.overlaySpinner}>
        <Loader variant="circular" size="lg" />
      </span>
    </div>
  )
}
```

- [ ] **Step 4: `NavProgress.tsx`**

```tsx
'use client'

import { useEffect, useRef, useState } from 'react'
import { usePathname, useSearchParams } from 'next/navigation'
import { useUiStore } from '@/store/uiStore'
import { MODO_INDICADOR_NAV } from '@/config/navIndicator'
import { BarraProgresso } from './BarraProgresso'
import { OverlayNav } from './OverlayNav'

const DELAY_MOSTRAR = 150
const MIN_VISIVEL = 400
const TIMEOUT_SEGURANCA = 10000

/** Debounce de aparição + hold mínimo: evita piscar em navegação instantânea e tremer em
 *  navegação rápida. Recebe o `navegando` cru do store, devolve o "deve mostrar" já suavizado. */
function useIndicadorVisivel(navegando: boolean): boolean {
  const [visivel, setVisivel] = useState(false)
  const mostradoEm = useRef<number | null>(null)

  useEffect(() => {
    let t: ReturnType<typeof setTimeout>
    if (navegando) {
      t = setTimeout(() => {
        mostradoEm.current = Date.now()
        setVisivel(true)
      }, DELAY_MOSTRAR)
    } else if (visivel) {
      const decorrido = mostradoEm.current ? Date.now() - mostradoEm.current : MIN_VISIVEL
      const resta = Math.max(0, MIN_VISIVEL - decorrido)
      t = setTimeout(() => {
        setVisivel(false)
        mostradoEm.current = null
      }, resta)
    }
    return () => clearTimeout(t)
  }, [navegando, visivel])

  return visivel
}

export function NavProgress() {
  const pathname = usePathname()
  const searchParams = useSearchParams()
  const navegando = useUiStore((s) => s.navegando)
  const iniciarNav = useUiStore((s) => s.iniciarNav)
  const finalizarNav = useUiStore((s) => s.finalizarNav)
  const resetarNav = useUiStore((s) => s.resetarNav)

  const visivel = useIndicadorVisivel(navegando)
  const primeiroRender = useRef(true)
  const seguranca = useRef<ReturnType<typeof setTimeout> | null>(null)

  // Patch de history: toda navegação client-side do Next passa por pushState/replaceState.
  useEffect(() => {
    const pushOriginal = history.pushState.bind(history)
    const replaceOriginal = history.replaceState.bind(history)

    const mudouRota = (url?: string | URL | null) => {
      if (url == null) return true
      try {
        const alvo = new URL(url, window.location.href)
        return alvo.pathname !== window.location.pathname || alvo.search !== window.location.search
      } catch {
        return true
      }
    }

    const armarSeguranca = () => {
      if (seguranca.current) clearTimeout(seguranca.current)
      seguranca.current = setTimeout(() => resetarNav(), TIMEOUT_SEGURANCA)
    }

    history.pushState = function (data, unused, url) {
      if (mudouRota(url)) {
        iniciarNav()
        armarSeguranca()
      }
      return pushOriginal(data, unused, url)
    }
    history.replaceState = function (data, unused, url) {
      if (mudouRota(url)) {
        iniciarNav()
        armarSeguranca()
      }
      return replaceOriginal(data, unused, url)
    }

    const aoVoltar = () => {
      iniciarNav()
      armarSeguranca()
    }
    window.addEventListener('popstate', aoVoltar)

    return () => {
      history.pushState = pushOriginal
      history.replaceState = replaceOriginal
      window.removeEventListener('popstate', aoVoltar)
      if (seguranca.current) clearTimeout(seguranca.current)
    }
  }, [iniciarNav, resetarNav])

  // Rota nova renderizou → fecha a navegação pendente.
  useEffect(() => {
    if (primeiroRender.current) {
      primeiroRender.current = false
      return
    }
    finalizarNav()
    if (seguranca.current) clearTimeout(seguranca.current)
  }, [pathname, searchParams, finalizarNav])

  if (MODO_INDICADOR_NAV === 'overlay') return <OverlayNav ativo={visivel} />
  return <BarraProgresso ativo={visivel} />
}
```

- [ ] **Step 5: Montar no layout — `(app)/layout.tsx`**

Adicionar o import:

```tsx
import { NavProgress } from '@/components/layout/NavProgress/NavProgress'
```

E dentro do `<AuthGuard>`, antes de `<FaixaOffline />`:

```tsx
    <AuthGuard>
      <NavProgress />
      <FaixaOffline />
```

- [ ] **Step 6: Simulador na rota de demo — `demo/loaders/page.tsx`**

Adicionar, ao fim do `wrapper`, uma seção que renderiza a barra e o overlay sob demanda (sem navegar). Import no topo:

```tsx
import { BarraProgresso } from '@/components/layout/NavProgress/BarraProgresso'
import { OverlayNav } from '@/components/layout/NavProgress/OverlayNav'
```

Estado + handlers dentro do componente:

```tsx
  const [simBarra, setSimBarra] = useState(false)
  const [simOverlay, setSimOverlay] = useState(false)

  function dispararBarra() {
    setSimBarra(true)
    setTimeout(() => setSimBarra(false), 2000)
  }
  function dispararOverlay() {
    setSimOverlay(true)
    setTimeout(() => setSimOverlay(false), 2000)
  }
```

JSX antes de fechar o `</div>` do wrapper:

```tsx
      <section className={styles.secao}>
        <h2 className={styles.secaoTitulo}>Indicador de navegação</h2>
        <div className={styles.simulador}>
          <button onClick={dispararBarra}>Simular barra (2s)</button>
          <button onClick={dispararOverlay}>Simular overlay (2s)</button>
        </div>
      </section>
      <BarraProgresso ativo={simBarra} />
      <OverlayNav ativo={simOverlay} />
```

- [ ] **Step 7: Verificar**

Run (de `frontend/`): `npx tsc --noEmit && npx eslint src/components/layout/NavProgress/ "src/app/(app)/layout.tsx" src/app/demo/ && npm run build`
Then `npm run dev`:
- `/demo/loaders` → botões "Simular barra"/"Simular overlay" mostram cada um por 2s.
- Logar no app, navegar pelo sidebar → barra fina azul aparece no topo em navegação que demora, some ao carregar.
- Voltar/avançar do navegador → barra aparece.

- [ ] **Step 8: Commit** (após checkpoint do pedaço B — Task 6)

---

## Task 6: Modo `barra-e-link` — ícone pendente no Sidebar

**Files:**
- Modify: `frontend/src/components/layout/Sidebar.tsx`

**Interfaces:**
- Consumes: `useLinkStatus` de `next/link`, `MODO_INDICADOR_NAV`, `Loader`.
- Produces: subcomponente local `IconePendente` (não exportado).

- [ ] **Step 1: Adicionar imports no `Sidebar.tsx`**

```tsx
import Link, { useLinkStatus } from 'next/link'
import { MODO_INDICADOR_NAV } from '@/config/navIndicator'
import { Loader } from '@/components/common/Loader/Loader'
```

(o `import Link from 'next/link'` que já existe vira o de cima)

- [ ] **Step 2: Subcomponente `IconePendente` (no mesmo arquivo, antes de `export function Sidebar`)**

```tsx
/** Renderiza o ícone do item; no modo 'barra-e-link', troca por um spinner enquanto a
 *  navegação disparada por este link está pendente. `useLinkStatus` só funciona como
 *  descendente de um <Link> do next. */
function IconePendente({ icon: Icon }: { icon: typeof Home }) {
  const { pending } = useLinkStatus()
  if (MODO_INDICADOR_NAV === 'barra-e-link' && pending) {
    return <Loader variant="circular" size="sm" />
  }
  return <Icon size={20} />
}
```

- [ ] **Step 3: Usar no `renderLink`**

Trocar `<Icon size={20} />` por `<IconePendente icon={item.icon} />` dentro do `<Link>` de `renderLink`. Remover a linha `const Icon = item.icon` se ficar sem uso (o lint acusa).

- [ ] **Step 4: Verificar**

Run (de `frontend/`): `npx tsc --noEmit && npx eslint src/components/layout/Sidebar.tsx`
Then, com `MODO_INDICADOR_NAV = 'barra-e-link'` temporariamente: `npm run dev`, logar, clicar num item do sidebar → o ícone daquele item vira spinner até a rota abrir. Voltar a constante pra `'barra'`.

- [ ] **Step 5: CHECKPOINT — pedaço B**

Avisar o autor: *"Pedaço B pronto — indicador de navegação global. Default é a barra fina no topo. Pra testar os outros dois modos, troca `MODO_INDICADOR_NAV` em `src/config/navIndicator.ts` pra `'overlay'` ou `'barra-e-link'` e sobe. Também dá pra ver os três no `/demo/loaders`. Me diz qual você quer de padrão."*

Esperar o retorno com a escolha. Ajustar a constante pro modo escolhido. Então commitar o pedaço B (Tasks 4, 5, 6) num commit:

```bash
git add frontend/src/store/uiStore.ts frontend/src/config/ frontend/src/components/layout/NavProgress/ "frontend/src/app/(app)/layout.tsx" frontend/src/components/layout/Sidebar.tsx frontend/src/app/demo/
git commit -m "feat(front): indicador de navegacao global com modo configuravel (barra/overlay/link)"
```

- [ ] **Step 6: Atualizar graphify** — `graphify update .`

---

## Task 6B: `<OverlayCarregando>` (componente de operação bloqueante)

> Feito na revisão. Substitui o `OverlayNav.tsx` (Task 5 Step 3), que foi removido.

**Files:**
- Create: `frontend/src/components/common/OverlayCarregando/OverlayCarregando.tsx`
- Create: `frontend/src/components/common/OverlayCarregando/OverlayCarregando.module.css`
- Modify: `frontend/src/components/layout/NavProgress/NavProgress.tsx` (remove branch de overlay)
- Modify: `frontend/src/components/layout/NavProgress/NavProgress.module.css` (tira estilos de overlay)
- Modify: `frontend/src/config/navIndicator.ts` (union `'barra' | 'barra-e-link'`, default `'barra-e-link'`)
- Modify: `frontend/src/app/demo/loaders/page.tsx` (seção "operação bloqueante" com `<OverlayCarregando>`)

**Interface produzida:**
- `OverlayCarregando({ ativo, texto?, cobertura? }: { ativo: boolean; texto?: string; cobertura?: 'fixed' | 'absolute' }): JSX.Element` — véu + `<Loader variant="circular" size="lg">` + texto opcional. `'fixed'` (default) cobre a viewport; `'absolute'` cobre o container-pai posicionado.

- [ ] Verificar: `npx tsc --noEmit && npx eslint src/components/common/OverlayCarregando/ src/components/layout/NavProgress/ src/app/demo/`

---

## Task 6C: Aplicar `<OverlayCarregando>` nas operações bloqueantes

**Files:**
- Modify: `PaymentBrickCheckout.tsx` + `.module.css` — overlay `cobertura="absolute"` no `reiniciando` (tela do Pix). **NÃO** no `enviando` do botão pagar (animação nativa do MP fica). `.wrapper` ganha `position: relative`.
- Modify: `ModalConfirmacaoCritica.tsx` + `.module.css` — overlay `cobertura="absolute"` no `isLoading`. `.modal` ganha `position: relative`.
- Modify: `ModalExcluirIgreja.tsx` + `.module.css` — overlay `cobertura="absolute"` no `carregando`. `.modal` ganha `position: relative`.
- Modify: `PessoaForm.tsx`, `EventoForm.tsx`, `MovimentacaoForm.tsx` — overlay `cobertura="fixed"` (default) no `isLoading`, texto `"Salvando…"` / `"Salvando alterações…"`.

- [ ] Verificar: `npx tsc --noEmit`, `npx eslint` nos arquivos (comparar com `git stash` — `BuscaGlobal.tsx` e `PessoaForm.tsx:cargoAtual` são erros pré-existentes, não novos), `npm run build`.

- [ ] **CHECKPOINT — pedaços B + B2.** Avisar o autor, esperar teste, então commit único:

```bash
git add frontend/src/store/uiStore.ts frontend/src/config/ frontend/src/components/layout/NavProgress/ frontend/src/components/common/OverlayCarregando/ "frontend/src/app/(app)/layout.tsx" frontend/src/components/layout/Sidebar.tsx frontend/src/app/demo/ frontend/src/components/common/ModalConfirmacaoCritica/ frontend/src/components/module/configuracoes/ModalExcluirIgreja/ frontend/src/components/module/pagamento/ frontend/src/components/module/pessoas/PessoaForm.tsx frontend/src/components/module/eventos/EventoForm.tsx frontend/src/components/module/movimentacoes/MovimentacaoForm.tsx
git commit -m "feat(front): indicador de navegacao (barra/link) + OverlayCarregando pra operacao bloqueante"
```

- [ ] `graphify update .`

---

## Task 7: Sidebar c1 — pílula de item ativo deslizante

**Files:**
- Modify: `frontend/src/components/layout/Sidebar.tsx`
- Modify: `frontend/src/components/layout/Sidebar.module.css`

**Interfaces:**
- Consumes: `usePathname` (já importado).
- Produces: nada exportado.

- [ ] **Step 1: CSS — `Sidebar.module.css`**

Adicionar `position: relative` em `.nav` (já é `display: flex; flex-direction: column`). Adicionar:

```css
.indicadorAtivo {
  position: absolute;
  left: 0;
  right: 0;
  border-radius: 0.5rem;
  background: #ffffff;
  box-shadow: 0 1px 2px 0 rgb(0 0 0 / 0.05);
  transition: transform 0.25s cubic-bezier(0.16, 1, 0.3, 1), height 0.25s cubic-bezier(0.16, 1, 0.3, 1), opacity 0.15s ease;
  pointer-events: none;
  z-index: 0;
}
.link, .grupoBotao { position: relative; z-index: 1; }
```

Trocar `.linkActive`: remover `background-color: #ffffff` e `box-shadow` (agora vêm da pílula), manter `color: #1d4ed8`.

```css
.linkActive {
  color: #1d4ed8;
}
```

```css
@media (prefers-reduced-motion: reduce) {
  .indicadorAtivo { transition: opacity 0.15s ease; }
}
```

- [ ] **Step 2: `Sidebar.tsx` — medir e posicionar**

Dentro de `Sidebar`, depois dos hooks existentes:

```tsx
  const navRef = useRef<HTMLElement | null>(null)
  const [pilula, setPilula] = useState<{ top: number; height: number; visivel: boolean }>({
    top: 0, height: 0, visivel: false,
  })
  const primeiraMedida = useRef(true)

  useLayoutEffect(() => {
    const nav = navRef.current
    if (!nav) return
    const ativo = nav.querySelector<HTMLElement>('[data-ativo="true"]')
    if (!ativo) {
      setPilula((p) => ({ ...p, visivel: false }))
      return
    }
    setPilula({ top: ativo.offsetTop, height: ativo.offsetHeight, visivel: true })
    primeiraMedida.current = false
  }, [pathname])
```

Imports: adicionar `useLayoutEffect`, `useRef` ao import de `react` (já tem `useState`).

- [ ] **Step 3: Marcar o link ativo e renderizar a pílula**

No `renderLink`, adicionar `data-ativo={ativo}` no `<Link>`. Nos botões de grupo (`grupoBotao`), adicionar `data-ativo={pathname.startsWith('/pessoas')}` / `data-ativo={pathname.startsWith('/configuracoes') || pathname === '/perfil'}` respectivamente.

Colocar `ref={navRef}` no `<nav className={styles.nav}>` e, como primeiro filho:

```tsx
      <nav className={styles.nav} ref={navRef}>
        <span
          className={styles.indicadorAtivo}
          style={{
            transform: `translateY(${pilula.top}px)`,
            height: pilula.height,
            opacity: pilula.visivel ? 1 : 0,
            transition: primeiraMedida.current ? 'none' : undefined,
          }}
          aria-hidden="true"
        />
        {filtrar(navItems).map(renderLink)}
```

- [ ] **Step 4: Verificar**

Run (de `frontend/`): `npx tsc --noEmit && npx eslint src/components/layout/Sidebar.tsx`
Then `npm run dev`: trocar de rota pelo sidebar → a pílula branca desliza suave até o item novo. Rota sem item correspondente (ex.: `/pessoas/visitantes` se "Visitantes" não estiver visível) → pílula some. Ativar reduce-motion no SO → reposiciona sem deslizar.

- [ ] **Step 5: Commit** (após checkpoint do pedaço C — Task 10)

---

## Task 8: Sidebar c2 — feedback de toque (só CSS)

**Files:**
- Modify: `frontend/src/components/layout/Sidebar.module.css`

**Interfaces:** nenhuma.

- [ ] **Step 1: Refinar `.link:active` e transições**

Trocar a regra `.link`:

```css
.link {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem 1rem;
  border-radius: 0.5rem;
  transition: background-color 0.18s ease, color 0.18s ease, transform 0.1s ease;
  -webkit-tap-highlight-color: transparent;
}
.link:active {
  transform: scale(0.98);
  background-color: #e0e7ff;
}
.link:active svg {
  transform: scale(0.92);
  transition: transform 0.1s ease;
}
```

- [ ] **Step 2: Verificar**

Run (de `frontend/`): `npx eslint --no-eslintrc --ext .css 2>/dev/null || true` (CSS não passa por eslint; só conferir visual)
`npm run dev`: no mobile (DevTools, touch), pressionar um item → afunda + ícone encolhe de leve, solta → volta suave.

- [ ] **Step 3: Commit** (após checkpoint do pedaço C)

---

## Task 9: Sidebar c3 — drawer mobile (fade + trava de scroll + arrastar pra fechar)

**Files:**
- Create: `frontend/src/hooks/useArrastarParaFechar.ts`
- Modify: `frontend/src/components/layout/Sidebar.tsx`
- Modify: `frontend/src/components/layout/Sidebar.module.css`

**Interfaces:**
- Consumes: nada externo.
- Produces:
  - `useArrastarParaFechar(opts: { aberta: boolean; aoFechar: () => void }): { handlers: { onTouchStart: (e: React.TouchEvent) => void; onTouchMove: (e: React.TouchEvent) => void; onTouchEnd: () => void }; estiloArraste: React.CSSProperties }`.

- [ ] **Step 1: `hooks/useArrastarParaFechar.ts`**

```ts
import { useRef, useState } from 'react'
import type { CSSProperties, TouchEvent } from 'react'

const LIMITE_FRACAO = 0.4 // arrastou 40% da largura → fecha
const FLICK_PX = 60 // ou um flick rápido de 60px

interface Opts {
  aberta: boolean
  aoFechar: () => void
}

/** Swipe-to-close pro drawer mobile: segue o dedo no translateX (só pra esquerda) e fecha
 *  se passar do limite ou for um flick rápido. Desktop nunca chama isto (sidebar é fixo). */
export function useArrastarParaFechar({ aberta, aoFechar }: Opts) {
  const inicioX = useRef<number | null>(null)
  const inicioT = useRef(0)
  const [delta, setDelta] = useState(0)
  const [arrastando, setArrastando] = useState(false)

  function onTouchStart(e: TouchEvent) {
    if (!aberta) return
    inicioX.current = e.touches[0].clientX
    inicioT.current = Date.now()
    setArrastando(true)
  }

  function onTouchMove(e: TouchEvent) {
    if (inicioX.current == null) return
    const d = e.touches[0].clientX - inicioX.current
    setDelta(Math.min(0, d)) // só pra esquerda
  }

  function onTouchEnd() {
    if (inicioX.current == null) return
    const larguraAside = 256 // .sidebar width: 16rem
    const dt = Date.now() - inicioT.current
    const passouLimite = Math.abs(delta) > larguraAside * LIMITE_FRACAO
    const flick = Math.abs(delta) > FLICK_PX && dt < 250
    if (passouLimite || flick) aoFechar()
    inicioX.current = null
    setDelta(0)
    setArrastando(false)
  }

  const estiloArraste: CSSProperties = arrastando
    ? { transform: `translateX(${delta}px)`, transition: 'none' }
    : {}

  return { handlers: { onTouchStart, onTouchMove, onTouchEnd }, estiloArraste }
}
```

- [ ] **Step 2: CSS — fade do backdrop**

Trocar `.overlay` / `.overlayVisivel`:

```css
.overlay {
  position: fixed;
  inset: 0;
  background: rgba(19, 27, 46, 0.4);
  z-index: 35;
  opacity: 0;
  visibility: hidden;
  transition: opacity 0.28s ease, visibility 0.28s ease;
}
.overlayVisivel {
  opacity: 1;
  visibility: visible;
}
```

No bloco `@media (min-width: 768px)`, trocar `.overlay { display: none !important; }` por `.overlay { display: none; }` (com `visibility`/`opacity` já não precisa do `!important`, mas manter `display: none` no desktop).

- [ ] **Step 3: `Sidebar.tsx` — aplicar hook + trava de scroll**

Imports: `import { useEffect } from 'react'` (somar aos de react), `import { useArrastarParaFechar } from '@/hooks/useArrastarParaFechar'`.

Dentro de `Sidebar`:

```tsx
  const { handlers, estiloArraste } = useArrastarParaFechar({ aberta: navAberta, aoFechar: fecharNav })

  // Trava o scroll do body enquanto o drawer está aberto no mobile.
  useEffect(() => {
    if (!navAberta) return
    const anterior = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => { document.body.style.overflow = anterior }
  }, [navAberta])
```

No `<aside>`:

```tsx
    <aside
      className={`${styles.sidebar} ${navAberta ? styles.sidebarAberta : ''}`}
      style={estiloArraste}
      {...handlers}
    >
```

- [ ] **Step 4: Verificar**

Run (de `frontend/`): `npx tsc --noEmit && npx eslint src/components/layout/Sidebar.tsx src/hooks/useArrastarParaFechar.ts`
Then `npm run dev` no viewport mobile (DevTools, touch on):
- Abrir o menu → backdrop faz fade, não aparece seco.
- Arrastar o drawer pra esquerda → segue o dedo; soltar além de ~40% → fecha; soltar antes → volta.
- Com o menu aberto, tentar rolar o conteúdo atrás → não rola.
- Desktop: nada muda, sidebar fixo.

- [ ] **Step 5: Commit** (após checkpoint do pedaço C)

---

## Task 10: Sidebar c4 — polimento do acordeão

**Files:**
- Modify: `frontend/src/components/layout/Sidebar.module.css`

**Interfaces:** nenhuma.

- [ ] **Step 1: Chevron + stagger dos itens**

Trocar `.seta`:

```css
.seta {
  flex-shrink: 0;
  transition: transform 0.2s cubic-bezier(0.16, 1, 0.3, 1);
}
```

Ajustar `.submenuWrap` (curva um tico mais suave):

```css
.submenuWrap {
  display: grid;
  grid-template-rows: 0fr;
  transition: grid-template-rows 0.26s cubic-bezier(0.16, 1, 0.3, 1);
}
```

Stagger nos `.subLink`:

```css
.subLink {
  padding: 0.625rem 0.75rem;
  border-radius: 0.5rem;
  font-size: 0.8125rem;
  font-weight: 500;
  color: #64748b;
  opacity: 0;
  transform: translateY(-4px);
  transition: background-color 0.15s ease, color 0.15s ease, transform 0.1s ease, opacity 0.18s ease;
}
.submenuAberto .subLink { opacity: 1; transform: translateY(0); }
.submenuAberto .subLink:nth-child(1) { transition-delay: 0.03s; }
.submenuAberto .subLink:nth-child(2) { transition-delay: 0.06s; }
.submenuAberto .subLink:nth-child(3) { transition-delay: 0.09s; }
.submenuAberto .subLink:nth-child(4) { transition-delay: 0.12s; }
```

Adicionar ao bloco `@media (prefers-reduced-motion: reduce)` já existente os seletores `.subLink`:

```css
@media (prefers-reduced-motion: reduce) {
  .sidebar, .submenuWrap, .seta, .link, .subLink {
    transition: none;
  }
  .subLink { opacity: 1; transform: none; }
}
```

- [ ] **Step 2: Verificar**

`npm run dev`: abrir "Pessoas"/"Configurações" no sidebar → itens entram em cascata rápida, chevron gira suave. reduce-motion → abre direto sem cascata.

- [ ] **Step 3: CHECKPOINT — pedaço C**

Avisar o autor: *"Pedaço C pronto — pílula ativa deslizante, feedback de toque, drawer com fade + arrastar pra fechar + trava de scroll, e cascata nos submenus. Testa no mobile (DevTools) e no desktop; ativa 'reduce motion' no SO e reconfere."*

Esperar "ok". Commitar o pedaço C (Tasks 7-10):

```bash
git add frontend/src/components/layout/Sidebar.tsx frontend/src/components/layout/Sidebar.module.css frontend/src/hooks/useArrastarParaFechar.ts
git commit -m "feat(front): polimento do sidebar — pilula ativa, swipe-to-close, trava de scroll, cascata nos submenus"
```

- [ ] **Step 4: Atualizar graphify** — `graphify update .`

---

## Task 10B: Peça D — animação em toda troca de conteúdo

> Adicionada na execução. Ver spec "Peça D". Sem dependência nova.

**Files (novos):**
- `frontend/src/components/common/Transicao/TransicaoRota.tsx`
- `frontend/src/components/common/Transicao/Transicao.tsx`
- `frontend/src/components/common/Transicao/ItemAnimado.tsx`
- `frontend/src/components/common/Transicao/Transicao.module.css`
- `frontend/src/hooks/useListaComSaida.ts`

**Files (modificados):**
- `frontend/src/app/(app)/layout.tsx` — `{children}` dentro de `<TransicaoRota>`
- 2–3 listas com add/remove pra `<ItemAnimado>` (ver Step 4)
- `frontend/src/app/demo/loaders/page.tsx` — seção demonstrando D1/D2/D3

**Interfaces produzidas:**
- `TransicaoRota({ children }: { children: React.ReactNode }): JSX.Element` — `key={usePathname()}` + keyframe de entrada.
- `Transicao({ children, modo, className }: { children: React.ReactNode; modo?: 'fade' | 'subir' | 'escala'; className?: string }): JSX.Element` — anima na montagem via `@starting-style`.
- `ItemAnimado({ children, saindo, className }: { children: React.ReactNode; saindo?: boolean; className?: string }): JSX.Element`.
- `useListaComSaida<T>(itens: T[], chave: (item: T) => string): Array<{ item: T; chave: string; saindo: boolean }>` — mantém no array por ~0.3s os itens que sumiram, marcados `saindo`, pra o `<ItemAnimado>` rodar a saída antes de desmontar.

- [ ] **Step 1: `Transicao.module.css`**

```css
/* D1 — conteúdo de rota: keyframe (re-dispara a cada navegação via key={pathname}) */
.rota { animation: entrarConteudo 0.28s cubic-bezier(0.22, 1, 0.36, 1); }
@keyframes entrarConteudo {
  from { opacity: 0; transform: translateY(8px); }
  to   { opacity: 1; transform: translateY(0); }
}

/* D2 — bloco fora de rota: transition + @starting-style (não re-dispara a cada render) */
.bloco {
  transition: opacity 0.24s ease, transform 0.24s cubic-bezier(0.22, 1, 0.36, 1);
  opacity: 1;
  transform: none;
}
.fade { @starting-style { opacity: 0; } }
.subir { @starting-style { opacity: 0; transform: translateY(10px); } }
.escala { @starting-style { opacity: 0; transform: scale(0.96); } }

/* D3 — item de lista */
.item {
  transition: opacity 0.22s ease, transform 0.22s cubic-bezier(0.22, 1, 0.36, 1),
              max-height 0.24s ease, margin 0.24s ease, padding 0.24s ease;
  overflow: hidden;
  opacity: 1;
}
.item {
  @starting-style { opacity: 0; transform: translateX(-6px); }
}
.itemSaindo {
  opacity: 0;
  transform: translateX(-6px);
  max-height: 0 !important;
  margin-top: 0 !important;
  margin-bottom: 0 !important;
  padding-top: 0 !important;
  padding-bottom: 0 !important;
}

@media (prefers-reduced-motion: reduce) {
  .rota { animation: none; }
  .bloco, .item { transition: opacity 0.15s ease; }
  .fade, .subir, .escala { @starting-style { opacity: 0; transform: none; } }
  .itemSaindo { transform: none; }
}
```

- [ ] **Step 2: `TransicaoRota.tsx`**

```tsx
'use client'

import { usePathname } from 'next/navigation'
import styles from './Transicao.module.css'

/** Faz o conteúdo da rota (e das abas, que são rotas) entrar animado a cada navegação.
 *  `key={pathname}` remonta a subárvore → o keyframe roda de novo. Sidebar/topbar ficam
 *  fora deste wrapper, então não piscam. */
export function TransicaoRota({ children }: { children: React.ReactNode }) {
  const pathname = usePathname()
  return (
    <div key={pathname} className={styles.rota}>
      {children}
    </div>
  )
}
```

- [ ] **Step 3: `Transicao.tsx` + `ItemAnimado.tsx` + `useListaComSaida.ts`**

```tsx
// Transicao.tsx
'use client'

import { clsx } from 'clsx'
import styles from './Transicao.module.css'

export function Transicao({
  children,
  modo = 'fade',
  className,
}: {
  children: React.ReactNode
  modo?: 'fade' | 'subir' | 'escala'
  className?: string
}) {
  return <div className={clsx(styles.bloco, styles[modo], className)}>{children}</div>
}
```

```tsx
// ItemAnimado.tsx
'use client'

import { clsx } from 'clsx'
import styles from './Transicao.module.css'

export function ItemAnimado({
  children,
  saindo = false,
  className,
}: {
  children: React.ReactNode
  saindo?: boolean
  className?: string
}) {
  return <div className={clsx(styles.item, saindo && styles.itemSaindo, className)}>{children}</div>
}
```

```ts
// useListaComSaida.ts
import { useEffect, useRef, useState } from 'react'

const DURACAO_SAIDA = 260

type Entrada<T> = { item: T; chave: string; saindo: boolean }

/** Mantém no array, por ~0.26s e marcados `saindo`, os itens que sumiram da lista de
 *  origem — pra o <ItemAnimado> rodar a animação de saída antes de desmontar. */
export function useListaComSaida<T>(itens: T[], chave: (item: T) => string): Entrada<T>[] {
  const [render, setRender] = useState<Entrada<T>[]>(() =>
    itens.map((item) => ({ item, chave: chave(item), saindo: false })),
  )
  const timers = useRef<Map<string, ReturnType<typeof setTimeout>>>(new Map())

  useEffect(() => {
    const chavesAtuais = new Set(itens.map(chave))
    setRender((anterior) => {
      const porChave = new Map(anterior.map((e) => [e.chave, e]))
      // atualiza/insere os presentes
      for (const item of itens) {
        porChave.set(chave(item), { item, chave: chave(item), saindo: false })
      }
      // marca saindo os que sumiram e agenda a remoção
      for (const e of anterior) {
        if (!chavesAtuais.has(e.chave) && !e.saindo) {
          porChave.set(e.chave, { ...e, saindo: true })
          if (!timers.current.has(e.chave)) {
            const t = setTimeout(() => {
              timers.current.delete(e.chave)
              setRender((r) => r.filter((x) => x.chave !== e.chave))
            }, DURACAO_SAIDA)
            timers.current.set(e.chave, t)
          }
        }
      }
      // preserva a ordem: primeiro os itens atuais, depois os que estão saindo
      const vivos = itens.map((item) => porChave.get(chave(item))!)
      const saindo = anterior.filter((e) => !chavesAtuais.has(e.chave)).map((e) => porChave.get(e.chave)!)
      return [...vivos, ...saindo]
    })
  }, [itens, chave])

  useEffect(() => {
    const mapa = timers.current
    return () => { mapa.forEach(clearTimeout); mapa.clear() }
  }, [])

  return render
}
```

- [ ] **Step 4: Aplicar**

`(app)/layout.tsx`:

```tsx
import { TransicaoRota } from '@/components/common/Transicao/TransicaoRota'
// ...
      <main className={styles.main}>
        <TransicaoRota>{children}</TransicaoRota>
      </main>
```

Listas com add/remove real (usar `useListaComSaida` + `<ItemAnimado>`):
- `frontend/src/components/module/eventos/` — lista de acompanhantes na inscrição (achar o arquivo: grep `acompanhante` em `components/module/eventos`).
- `frontend/src/components/module/movimentacoes/MovimentacaoForm.tsx` — `contribuintesArray` (linhas de contribuinte).
- `frontend/src/components/module/eventos/EventoForm.tsx` — campos personalizados do builder, se a lista for dinâmica.

Padrão de aplicação:

```tsx
const linhas = useListaComSaida(campos, (c) => c.id)
// ...
{linhas.map(({ item, chave, saindo }) => (
  <ItemAnimado key={chave} saindo={saindo}>
    {/* conteúdo da linha, usando `item` */}
  </ItemAnimado>
))}
```

- [ ] **Step 5: Demo** — em `demo/loaders/page.tsx`, seção "Transições" com: um botão que troca um bloco via `<Transicao modo="subir">`, e uma mini-lista com adicionar/remover usando `useListaComSaida` + `<ItemAnimado>`.

- [ ] **Step 6: Verificar**

`npx tsc --noEmit && npx eslint src/components/common/Transicao/ src/hooks/useListaComSaida.ts "src/app/(app)/layout.tsx" <arquivos de lista> && npm run build`

Manual: navegar entre rotas/abas → conteúdo entra com fade+subir; adicionar/remover contribuinte → linha anima entrada e colapsa na saída; `reduce motion` → só opacity.

- [ ] **Step 7: CHECKPOINT — pedaço D.** Avisar, esperar teste, commit:

```bash
git add frontend/src/components/common/Transicao/ frontend/src/hooks/useListaComSaida.ts "frontend/src/app/(app)/layout.tsx" frontend/src/app/demo/ <arquivos de lista>
git commit -m "feat(front): transicoes de entrada em rota/abas, blocos e itens de lista"
```

- [ ] **Step 8:** `graphify update .`

---

## Task 11: Limpeza — remover variantes de loader não escolhidas

**Files:**
- Modify: `frontend/src/components/common/Loader/Loader.tsx`
- Modify: `frontend/src/components/common/Loader/Loader.module.css`
- Modify: `frontend/src/app/demo/loaders/page.tsx` (ou remover a rota)

**Interfaces:** reduz a union `LoaderVariant` para as variantes escolhidas.

- [ ] **Step 1:** Com a lista de variantes que o autor escolheu no `/demo/loaders`, remover de `Loader.tsx` os componentes de variante não usados, os `case` correspondentes no `switch`, e reduzir a union `LoaderVariant`. Remover de `Loader.module.css` as classes e `@keyframes` órfãos.

- [ ] **Step 2:** Decidir com o autor: manter `/demo/loaders` (dev-only, útil pra referência futura) ou remover `src/app/demo/`.

- [ ] **Step 3: Verificar**

Run (de `frontend/`): `npx tsc --noEmit && npx eslint src/ && npm run build`

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/common/Loader/ frontend/src/app/demo/
git commit -m "chore(front): remove variantes de loader nao usadas"
```

- [ ] **Step 5:** `graphify update .`

---

## Self-Review

**Spec coverage:**
- Peça A (biblioteca Loader, 12 variantes, CSS Modules, mapa de cor, reduced-motion, demo, trocas) → Tasks 1, 2, 3. ✓
- Peça B (uiStore, config, NavProgress, patch de history, delay/min-visível, timeout, barra, overlay, modo link no sidebar, simulador no demo) → Tasks 4, 5, 6. ✓
- Peça C (c1 pílula, c2 toque, c3 drawer fade + swipe + scroll lock, c4 acordeão) → Tasks 7, 8, 9, 10. ✓
- Limpeza pós-escolha → Task 11. ✓
- Ordem/checkpoints/commit-após-teste → Steps de CHECKPOINT nas Tasks 3, 6, 10. ✓

**Placeholder scan:** sem "TBD"/"TODO"/"handle edge cases" genéricos. Cada step tem código real ou comando real. O único ponto dependente de input humano é a Task 11 (lista de variantes escolhidas) — que é o objetivo declarado do demo, não um placeholder de plano.

**Type consistency:**
- `useUiStore`: `iniciarNav`/`finalizarNav`/`resetarNav`/`navegando` — definidos na Task 4, consumidos igual na Task 5. ✓
- `MODO_INDICADOR_NAV` (`'barra'|'overlay'|'barra-e-link'`) — Task 4, consumido nas Tasks 5 e 6 com os mesmos literais. ✓
- `Loader` / `LoaderVariant` — Task 1, consumidos nas Tasks 2, 3, 5, 6 com a mesma assinatura (`variant`, `size`, `text`, `className`). ✓
- `BarraProgresso`/`OverlayNav` com prop `{ ativo: boolean }` — Task 5, reusados no simulador (Task 5 Step 6) com a mesma prop. ✓
- `useArrastarParaFechar` retorna `{ handlers, estiloArraste }` — Task 9, consumido igual no Sidebar. ✓
- `IconePendente({ icon })` — Task 6, tipo `typeof Home` (padrão já usado no arquivo pro tipo de ícone lucide). ✓
