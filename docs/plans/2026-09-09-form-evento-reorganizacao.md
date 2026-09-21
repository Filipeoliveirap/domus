# Reorganização do formulário de evento — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reorganizar `EventoForm` numa coluna única (PC = mobile), com blocos recolhíveis pro conteúdo opcional, toggles renomeados, botão salvar sempre ativo + rolagem suave até o primeiro erro, e uma prévia ao vivo do evento.

**Architecture:** Só front. Três componentes/hook novos e reusáveis (`useRolarParaErro`, `<BlocoRecolhivel>`, `<PreviaEvento>`), depois a reestrutura do `EventoForm.tsx` + limpeza do CSS. Nenhuma mudança de payload, validação Zod, contrato de API ou backend.

**Tech Stack:** Next.js 16 (App Router), React 19, TypeScript, react-hook-form + zod, CSS Modules, `src/styles/globals.css` para classes globais.

**Spec:** `backend/api/docs/superpowers/specs/2026-09-09-form-evento-reorganizacao-design.md`

## Global Constraints

- **Sem teste automatizado de front** — não há Jest/Vitest/Playwright no projeto. Verificação de cada task = `npm run build` limpo + `npm run lint` sem erro novo + checagem manual no `npm run dev` (porta 3000). `next start` está quebrado (`output: "standalone"` no `next.config.ts`) — sempre `npm run dev` local.
- **Toda animação** vem com bloco `@media (prefers-reduced-motion: reduce)` que zera `transform`/`transition`/`animation`. Sem exceção.
- **`@media (hover: hover)`** isola qualquer efeito de `:hover` (touch "gruda" o `:hover` depois do tap).
- **Visibilidade** por `el.hidden` / atributo `hidden`, nunca `style.display`.
- **Nenhuma mudança** em: `useEventoForm.ts` (só é consumido), `useAppForm.ts`, `src/lib/validators.ts`, backend, `cadastrar/page.tsx`, `[id]/page.tsx`.
- **Rótulos e placeholders de campo** mantêm o padrão atual (rótulo em CAIXA ALTA com `*` para obrigatório; exemplo concreto no `placeholder`).
- **Commits:** mensagem em português normal (não caveman). Terminar com:
  ```
  Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_01PgEuCSiVzUaTXWAUfwHjg5
  ```
- **`git add`** só nos arquivos tocados — nunca `git add -A` / `git add .`.
- Branch já criada: `feat/form-evento-reorganizacao` (a spec já está commitada nela).

## Textos — antes → depois (referência para as tasks 5 e 6)

| Onde | Antes | Depois |
|---|---|---|
| toggle `requerInscricao` título | `Requer inscrição prévia` | `Exigir inscrição` |
| toggle `requerInscricao` descrição | `Ative para controlar vagas, preço e restrições de quem pode participar.` | `Participantes se inscrevem antes. Você controla vagas, prazo e valor.` |
| toggle `controlaPresenca` título | `Controlar presença` | `Check-in` |
| toggle `controlaPresenca` descrição | `Ative para marcar quem realmente compareceu e ver o relatório de presença deste evento.` | `Marque quem chegou no dia e veja quantos vieram.` |
| toggle `repetir` descrição | `Cadastre uma vez e as próximas ocorrências aparecem sozinhas.` | `As próximas datas entram na agenda sozinhas.` |
| `infoBox` na seção de data | `O evento aparecerá na agenda da igreja assim que for salvo.` | *(remover a `<div className={styles.infoBox}>` inteira)* |
| hint "Inscrições até" | `Ex.: 15/03/2026 23:59 — deixe vazio pra aceitar inscrições até o evento começar.` | `Vazio = aceita inscrição até o evento começar.` |
| hint política cancelamento | `Antes do prazo, o cancelamento é sempre livre e com reembolso total.` | `Antes do prazo, cancelar é sempre livre e com reembolso.` |
| hint preço | `Cobrado automaticamente na inscrição, através da conta de recebimento conectada pela igreja.` | `Cobrado na hora da inscrição.` |
| hint vagas | `Deixe vazio para não limitar.` | `Vazio = sem limite de vagas.` |
| banner de erro de validação | `Alguns campos precisam de atenção — confira os destaques em vermelho.` | `Faltou preencher um campo — te levei até ele.` |
| toggle "Apenas minha igreja" título | `Apenas minha igreja` | `Só minha igreja` |
| toggle "Apenas minha igreja" descrição | `Ative para este evento não aparecer para {concordar(...)} demais {congregacao.plural}.` | `Não mostra este evento para {concordar(congregacao.genero, 'as')} outras {congregacao.plural.toLowerCase()}.` |

---

## File Structure

**Novos**
- `frontend/src/hooks/forms/useRolarParaErro.ts` — hook genérico "rola até o 1º erro".
- `frontend/src/components/common/BlocoRecolhivel/BlocoRecolhivel.tsx` — disclosure (cabeçalho clicável + corpo que expande).
- `frontend/src/components/common/BlocoRecolhivel/BlocoRecolhivel.module.css`
- `frontend/src/components/module/eventos/PreviaEvento.tsx` — prévia ao vivo (apresentacional) + `resumirRegras()` pura.
- `frontend/src/components/module/eventos/PreviaEvento.module.css`

**Modificados**
- `frontend/src/styles/globals.css` — classe global `.tremido` (shake) + reduced-motion.
- `frontend/src/components/common/input/Input.tsx` — `data-campo-erro` no `<span>` de erro.
- `frontend/src/components/common/CampoData/CampoData.tsx` — `data-campo-erro` no `<span>` de erro.
- `frontend/src/components/module/eventos/SeletorLocal.tsx` — `data-campo-erro` nos `<span className={styles.erro}>`.
- `frontend/src/components/module/eventos/BlocoParaQuemE.tsx` — `data-campo-erro` no erro de idade máxima.
- `frontend/src/components/module/eventos/EventoForm.tsx` — reestrutura JSX, textos, botão sempre ativo, liga o hook, `camposFaltando`, `<PreviaEvento>`.
- `frontend/src/components/module/eventos/EventoForm.module.css` — coluna única, remove classes mortas, ajustes mobile.

---

## Task 1: Hook `useRolarParaErro` + classe global `.tremido`

**Files:**
- Create: `frontend/src/hooks/forms/useRolarParaErro.ts`
- Modify: `frontend/src/styles/globals.css` (append no fim)

**Interfaces:**
- Consumes: nada.
- Produces:
  ```ts
  export function useRolarParaErro(
    formRef: React.RefObject<HTMLFormElement | null>
  ): { rolarParaErro: () => void }
  ```
  - `rolarParaErro()` deve ser chamado dentro do callback de erro do `handleSubmit(onValid, onInvalid)` do react-hook-form.
  - Efeito colateral: dispara `window.dispatchEvent(new CustomEvent('domus:abrir-recolhivel', { detail: { id } }))` para cada `<BlocoRecolhivel>` fechado que contenha um `[data-campo-erro]`; depois rola até o primeiro `[data-campo-erro]` do form, foca o control do campo, e aplica a classe `tremido` por 450ms.
- Marcador esperado no DOM (fornecido pelas tasks 2 e 3): elementos de mensagem de erro têm `data-campo-erro`; blocos recolhíveis têm um wrapper `[data-recolhivel][data-id="<id>"]` e ficam com `hidden` quando fechados.

- [ ] **Step 1: Criar o hook**

Cria `frontend/src/hooks/forms/useRolarParaErro.ts`:

```ts
import { useCallback } from 'react'

/**
 * Leva o usuário até o primeiro campo com erro depois de um submit inválido:
 * abre qualquer <BlocoRecolhivel> fechado que contenha erro, rola suave até o
 * primeiro [data-campo-erro] (= primeiro no DOM = primeiro na ordem visual, já
 * que o form é coluna única), foca o control e dá um "tremido" curto.
 *
 * Genérico — não conhece o form de evento. Ligue no callback de erro do
 * handleSubmit: handleSubmit(onValid, () => { rolarParaErro(); ...banner })
 */
export function useRolarParaErro(formRef: React.RefObject<HTMLFormElement | null>) {
  const rolarParaErro = useCallback(() => {
    const form = formRef.current
    if (!form) return

    const reduzMovimento = window.matchMedia('(prefers-reduced-motion: reduce)').matches

    // Deixa o React pintar os aria-invalid / mensagens de erro antes de procurar.
    requestAnimationFrame(() => {
      // 1. Abre blocos recolhíveis fechados que contenham erro.
      form.querySelectorAll<HTMLElement>('[data-recolhivel][hidden]').forEach((bloco) => {
        if (bloco.querySelector('[data-campo-erro]')) {
          const id = bloco.getAttribute('data-id')
          if (id) window.dispatchEvent(new CustomEvent('domus:abrir-recolhivel', { detail: { id } }))
        }
      })

      // 2. Segunda rAF: o bloco já abriu, agora o alvo existe no layout.
      requestAnimationFrame(() => {
        const erro = form.querySelector<HTMLElement>('[data-campo-erro]')
        if (!erro) return

        // Container do campo: sobe até achar algo com [data-campo-foco] ou o pai
        // mais próximo que tenha um control focável.
        const container =
          erro.closest<HTMLElement>('[data-campo-foco]') ??
          erro.closest<HTMLElement>('label, .campo, section') ??
          erro.parentElement ??
          erro

        container.scrollIntoView({
          behavior: reduzMovimento ? 'auto' : 'smooth',
          block: 'center',
        })

        const focavel = container.querySelector<HTMLElement>(
          'input:not([type="hidden"]), textarea, select, button, [tabindex]',
        )
        focavel?.focus({ preventScroll: true })

        if (!reduzMovimento) {
          const alvoTremido = focavel ?? container
          alvoTremido.classList.add('tremido')
          window.setTimeout(() => alvoTremido.classList.remove('tremido'), 450)
        }
      })
    })
  }, [formRef])

  return { rolarParaErro }
}
```

- [ ] **Step 2: Adicionar a classe global `.tremido`**

No fim de `frontend/src/styles/globals.css`, adiciona:

```css
/* ─── Tremido: micro-feedback de "olha aqui" num campo com erro (useRolarParaErro) ─── */
.tremido {
  animation: domusTremido 0.4s cubic-bezier(0.36, 0.07, 0.19, 0.97);
}
@keyframes domusTremido {
  10%, 90% { transform: translateX(-1px); }
  20%, 80% { transform: translateX(2px); }
  30%, 50%, 70% { transform: translateX(-4px); }
  40%, 60% { transform: translateX(4px); }
}
@media (prefers-reduced-motion: reduce) {
  .tremido { animation: none; }
}
```

- [ ] **Step 3: Verificar build e lint**

```bash
cd frontend && npm run lint && npm run build
```
Esperado: sem erro. O hook ainda não é usado — só precisa compilar.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/hooks/forms/useRolarParaErro.ts frontend/src/styles/globals.css
git commit -m "feat(front): hook useRolarParaErro + classe .tremido

Rola suave até o primeiro campo com erro depois de submit inválido, abre
bloco recolhível que contenha o erro, foca o control e dá um tremido curto.
Genérico — ainda não ligado a nenhum form.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01PgEuCSiVzUaTXWAUfwHjg5"
```

---

## Task 2: Componente `<BlocoRecolhivel>`

**Files:**
- Create: `frontend/src/components/common/BlocoRecolhivel/BlocoRecolhivel.tsx`
- Create: `frontend/src/components/common/BlocoRecolhivel/BlocoRecolhivel.module.css`

**Interfaces:**
- Consumes: escuta `window` pelo evento `domus:abrir-recolhivel` (`detail.id`) — disparado pelo `useRolarParaErro` (Task 1).
- Produces:
  ```tsx
  interface BlocoRecolhivelProps {
    id: string
    titulo: string
    descricao?: string
    icone?: React.ReactNode
    defaultAberto?: boolean
    children: React.ReactNode
  }
  export function BlocoRecolhivel(props: BlocoRecolhivelProps): JSX.Element
  ```
  - Renderiza um `<div>` externo, um `<button type="button">` de cabeçalho com `aria-expanded`/`aria-controls`, e um corpo `<div id={`recolhivel-${id}`} role="region" hidden={!aberto} data-recolhivel data-id={id}>`.
  - Quando `aberto`, o corpo anima altura via `grid-template-rows: 0fr → 1fr`.

- [ ] **Step 1: Criar o componente**

`frontend/src/components/common/BlocoRecolhivel/BlocoRecolhivel.tsx`:

```tsx
'use client'

import { useEffect, useId, useState } from 'react'
import { ChevronRight } from 'lucide-react'
import styles from './BlocoRecolhivel.module.css'

interface BlocoRecolhivelProps {
  /** id estável — usado no aria e no evento "abrir por causa de erro". */
  id: string
  titulo: string
  descricao?: string
  icone?: React.ReactNode
  /** Abre já expandido (ex.: edição de evento que já tem restrição ativa). */
  defaultAberto?: boolean
  children: React.ReactNode
}

/**
 * Disclosure: cabeçalho clicável + corpo que expande suave. Para conteúdo
 * opcional que a maioria dos eventos não usa (recorrência, restrição de
 * público, campos personalizados) — some do caminho até alguém pedir.
 *
 * Diferente do <Revelar> (que é controlado por um booleano externo, tipo
 * `{toggle && <Revelar>}`): aqui o estado aberto/fechado é do próprio bloco.
 *
 * O useRolarParaErro dispara `domus:abrir-recolhivel` com o id deste bloco
 * quando há um campo com erro escondido aqui dentro.
 */
export function BlocoRecolhivel({
  id, titulo, descricao, icone, defaultAberto = false, children,
}: BlocoRecolhivelProps) {
  const [aberto, setAberto] = useState(defaultAberto)
  const corpoId = `recolhivel-${id}`
  const reactId = useId()

  useEffect(() => {
    function aoAbrirPorErro(e: Event) {
      const detail = (e as CustomEvent<{ id: string }>).detail
      if (detail?.id === id) setAberto(true)
    }
    window.addEventListener('domus:abrir-recolhivel', aoAbrirPorErro)
    return () => window.removeEventListener('domus:abrir-recolhivel', aoAbrirPorErro)
  }, [id])

  return (
    <div className={styles.bloco}>
      <button
        type="button"
        className={styles.cabecalho}
        aria-expanded={aberto}
        aria-controls={corpoId}
        onClick={() => setAberto((v) => !v)}
      >
        <ChevronRight
          size={18}
          className={`${styles.chevron} ${aberto ? styles.chevronAberto : ''}`}
          aria-hidden="true"
        />
        {icone && <span className={styles.icone} aria-hidden="true">{icone}</span>}
        <span className={styles.textos}>
          <span className={styles.titulo}>{titulo}</span>
          {descricao && <span className={styles.descricao}>{descricao}</span>}
        </span>
      </button>

      <div
        id={corpoId}
        role="region"
        aria-labelledby={reactId}
        hidden={!aberto}
        data-recolhivel
        data-id={id}
        className={styles.corpoWrap}
      >
        <div className={styles.corpoInner}>{children}</div>
      </div>
    </div>
  )
}
```

- [ ] **Step 2: Criar o CSS**

`frontend/src/components/common/BlocoRecolhivel/BlocoRecolhivel.module.css`:

```css
.bloco {
  border: 1px dashed var(--color-border-input);
  border-radius: var(--radius-md);
  background: var(--color-bg-white);
}

.cabecalho {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 14px 16px;
  min-height: 48px;
  text-align: left;
  background: none;
  border: none;
  cursor: pointer;
  border-radius: var(--radius-md);
  transition: background-color var(--transition-fast), transform var(--transition-fast);
}
@media (hover: hover) {
  .cabecalho:hover { background: var(--color-bg-table-header); }
}
.cabecalho:active { transform: scale(0.995); }
.cabecalho:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: -2px;
}

.chevron {
  flex-shrink: 0;
  color: var(--color-text-muted);
  transition: transform var(--transition-fast);
}
.chevronAberto { transform: rotate(90deg); }

.icone {
  display: flex;
  flex-shrink: 0;
  color: var(--color-primary);
}

.textos {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.titulo {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-primary);
}
.descricao {
  font-size: var(--font-size-xs);
  color: var(--color-text-muted);
}

/* Expansão de altura suave sem número mágico. */
.corpoWrap {
  display: grid;
  grid-template-rows: 1fr;
  transition: grid-template-rows 0.28s cubic-bezier(0.22, 1, 0.36, 1);
}
.corpoInner {
  overflow: hidden;
  min-height: 0;
  padding: 0 16px 16px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

@media (prefers-reduced-motion: reduce) {
  .cabecalho, .chevron, .corpoWrap { transition: none; }
}
```

Nota: o `hidden` no wrapper já colapsa o bloco quando fechado; a transição de `grid-template-rows` cobre o caso de abrir/fechar sem desmontar. Como o React aplica/remove `hidden` de forma síncrona, o efeito é um colapso limpo — aceitável para v1 (mesma abordagem pragmática do `<Revelar>`).

- [ ] **Step 3: Verificar build e lint**

```bash
cd frontend && npm run lint && npm run build
```
Esperado: sem erro. Componente ainda não usado.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/common/BlocoRecolhivel/
git commit -m "feat(front): componente BlocoRecolhivel (disclosure)

Cabeçalho clicável + corpo que expande suave, estado próprio (ao contrário
do Revelar). Escuta domus:abrir-recolhivel pra abrir quando um campo com
erro está escondido aqui dentro.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01PgEuCSiVzUaTXWAUfwHjg5"
```

---

## Task 3: Marcadores `data-campo-erro` nos componentes de campo

**Files:**
- Modify: `frontend/src/components/common/input/Input.tsx` (o `<span className={styles.fieldError}>`)
- Modify: `frontend/src/components/common/CampoData/CampoData.tsx` (o `<span className={styles.erro}>` na linha ~102)
- Modify: `frontend/src/components/module/eventos/SeletorLocal.tsx` (os `<span className={styles.erro}>` e o `<span className={styles.erro}>` de "buscando CEP…" **não** — só os de erro real)
- Modify: `frontend/src/components/module/eventos/BlocoParaQuemE.tsx` (o `<Input id="idade-max" error={erroIdadeMax} />` já é um `<Input>`, então herda o marcador do Input — **nada a fazer aqui**, confirmar)

**Interfaces:**
- Consumes: nada.
- Produces: todo elemento de mensagem de erro renderizado por esses componentes passa a ter o atributo `data-campo-erro` — é o seletor que o `useRolarParaErro` (Task 1) usa pra achar o primeiro erro.

- [ ] **Step 1: `Input.tsx`**

Troca:
```tsx
        {error && (
          <span className={styles.fieldError} role="alert">
            {error}
          </span>
        )}
```
por:
```tsx
        {error && (
          <span className={styles.fieldError} role="alert" data-campo-erro>
            {error}
          </span>
        )}
```

- [ ] **Step 2: `CampoData.tsx`**

Troca:
```tsx
      {erro && <span className={styles.erro}>{erro}</span>}
```
por:
```tsx
      {erro && <span className={styles.erro} data-campo-erro>{erro}</span>}
```

- [ ] **Step 3: `SeletorLocal.tsx`**

Nas 4 ocorrências de `{error && <span className={styles.erro}>{error}</span>}` (linhas ~286, ~300, ~314, ~358) e na de `uf` (`{errosEndereco?.uf && <span className={styles.erro}>{errosEndereco.uf}</span>}`, linha ~266), adiciona `data-campo-erro`:
```tsx
{error && <span className={styles.erro} data-campo-erro>{error}</span>}
```
```tsx
{errosEndereco?.uf && <span className={styles.erro} data-campo-erro>{errosEndereco.uf}</span>}
```
**Não** adicionar em `{carregandoCep && <span className={styles.erro}>buscando CEP…</span>}` (linha ~240) — não é erro.

- [ ] **Step 4: Confirmar `BlocoParaQuemE.tsx`**

Abre o arquivo e confirma que o erro de idade máxima passa por `<Input id="idade-max" error={erroIdadeMax} .../>` (é o caso hoje). Se for `<Input>`, herda o `data-campo-erro` do Step 1 — nenhuma mudança neste arquivo. Se houver algum `<span>` de erro solto, adiciona `data-campo-erro` nele.

- [ ] **Step 5: Verificar build e lint**

```bash
cd frontend && npm run lint && npm run build
```
Esperado: sem erro.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components/common/input/Input.tsx frontend/src/components/common/CampoData/CampoData.tsx frontend/src/components/module/eventos/SeletorLocal.tsx
git commit -m "feat(front): data-campo-erro nas mensagens de erro de campo

Marcador que o useRolarParaErro usa pra achar o primeiro campo com erro no
DOM. Input, CampoData e SeletorLocal.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01PgEuCSiVzUaTXWAUfwHjg5"
```

---

## Task 4: Componente `<PreviaEvento>` + `resumirRegras()`

**Files:**
- Create: `frontend/src/components/module/eventos/PreviaEvento.tsx`
- Create: `frontend/src/components/module/eventos/PreviaEvento.module.css`

**Interfaces:**
- Consumes: `RestricaoEstadoCivil`, `RestricaoSexo` de `@/types/evento.type`. `formatarValorDigitado` de `@/lib/formats/financeiro/movimentacaoFormat` (formata `"30.00"` → `"R$ 30,00"`).
- Produces:
  ```tsx
  export interface PreviaEventoProps {
    titulo?: string
    tipo?: string
    inicioData?: string          // ISO "2026-03-15"
    inicioHora?: string          // "19:00"
    fimData?: string
    localResumo?: string         // já resolvido pelo EventoForm; "" quando não há
    fotoId?: string | null
    requerInscricao: boolean
    tipoInscricao: 'GRATUITO' | 'PAGO'
    preco?: string               // "30.00"
    vagas?: number
    inscricoesAteData?: string   // ISO
    exclusivoMembros: boolean
    idadeMin?: number
    idadeMax?: number
    restricaoEstadoCivil?: RestricaoEstadoCivil | null
    restricaoSexo?: RestricaoSexo | null
    restritoPropriaIgreja: boolean
    temFamilia: boolean
    rotuloOutrasCongregacoes: string   // ex.: "as outras congregações" — montado no EventoForm via useRotulos
    controlaPresenca: boolean
    camposFaltando: string[]     // ["título", "data de início"] — vazio quando nada falta
  }
  export function PreviaEvento(props: PreviaEventoProps): JSX.Element
  export function resumirRegras(props: PreviaEventoProps): { texto: string; faltando: boolean }[]
  ```

- [ ] **Step 1: Criar `PreviaEvento.tsx`**

```tsx
'use client'

import { CalendarClock } from 'lucide-react'
import { formatarValorDigitado } from '@/lib/formats/financeiro/movimentacaoFormat'
import type { RestricaoEstadoCivil, RestricaoSexo } from '@/types/evento.type'
import styles from './PreviaEvento.module.css'

export interface PreviaEventoProps {
  titulo?: string
  tipo?: string
  inicioData?: string
  inicioHora?: string
  fimData?: string
  localResumo?: string
  fotoId?: string | null
  requerInscricao: boolean
  tipoInscricao: 'GRATUITO' | 'PAGO'
  preco?: string
  vagas?: number
  inscricoesAteData?: string
  exclusivoMembros: boolean
  idadeMin?: number
  idadeMax?: number
  restricaoEstadoCivil?: RestricaoEstadoCivil | null
  restricaoSexo?: RestricaoSexo | null
  restritoPropriaIgreja: boolean
  temFamilia: boolean
  rotuloOutrasCongregacoes: string
  controlaPresenca: boolean
  camposFaltando: string[]
}

const DIAS_ABREV = ['dom', 'seg', 'ter', 'qua', 'qui', 'sex', 'sáb']
const MESES_ABREV = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez']

/** "2026-03-15" -> "sáb, 15 mar". String vazia/ inválida -> "". */
function formatarDataCurta(iso?: string): string {
  if (!iso) return ''
  const [ano, mes, dia] = iso.split('-').map(Number)
  if (!ano || !mes || !dia) return ''
  const d = new Date(ano, mes - 1, dia)
  return `${DIAS_ABREV[d.getDay()]}, ${dia} ${MESES_ABREV[mes - 1]}`
}

/** Junta ["a", "b", "c"] -> "a, b e c". */
function juntarComE(itens: string[]): string {
  if (itens.length <= 1) return itens.join('')
  return `${itens.slice(0, -1).join(', ')} e ${itens[itens.length - 1]}`
}

/**
 * Regras do evento em português, uma frase por linha. `faltando: true` = linha
 * vermelha de "falta preencher". Ordem: faltando primeiro, depois as regras.
 */
export function resumirRegras(p: PreviaEventoProps): { texto: string; faltando: boolean }[] {
  const linhas: { texto: string; faltando: boolean }[] = []

  if (p.camposFaltando.length > 0) {
    linhas.push({ texto: `Falta preencher: ${juntarComE(p.camposFaltando)}`, faltando: true })
  }

  const temRestricaoPublico =
    p.exclusivoMembros || p.idadeMin != null || p.idadeMax != null ||
    p.restricaoEstadoCivil != null || p.restricaoSexo != null

  if (!p.requerInscricao) {
    linhas.push({
      texto: temRestricaoPublico ? 'Sem inscrição' : 'Aberto a todos · sem inscrição',
      faltando: false,
    })
  } else {
    const partes: string[] = ['Inscrição obrigatória']
    partes.push(p.vagas != null ? `${p.vagas} vagas` : 'vagas ilimitadas')
    if (p.inscricoesAteData) partes.push(`inscrições até ${formatarDataCurta(p.inscricoesAteData)}`)
    partes.push(
      p.tipoInscricao === 'PAGO' && p.preco
        ? formatarValorDigitado(p.preco)
        : 'gratuito',
    )
    linhas.push({ texto: partes.join(' · '), faltando: false })
  }

  if (p.exclusivoMembros) linhas.push({ texto: 'Só para membros', faltando: false })

  if (p.idadeMin != null || p.idadeMax != null) {
    const faixa =
      p.idadeMin != null && p.idadeMax != null ? `${p.idadeMin}–${p.idadeMax} anos`
        : p.idadeMin != null ? `a partir de ${p.idadeMin} anos`
          : `até ${p.idadeMax} anos`
    linhas.push({ texto: faixa, faltando: false })
  }

  if (p.restricaoSexo === 'MULHER') linhas.push({ texto: 'Somente mulheres', faltando: false })
  if (p.restricaoSexo === 'HOMEM') linhas.push({ texto: 'Somente homens', faltando: false })

  if (p.restricaoEstadoCivil) {
    const mapa: Record<RestricaoEstadoCivil, string> = {
      SOLTEIRO: 'apenas solteiros(as)',
      CASADO: 'apenas casados(as)',
      DIVORCIADO: 'apenas divorciados(as)',
      VIUVO: 'apenas viúvos(as)',
    }
    linhas.push({ texto: mapa[p.restricaoEstadoCivil], faltando: false })
  }

  if (p.restritoPropriaIgreja && p.temFamilia) {
    linhas.push({ texto: `Não aparece para ${p.rotuloOutrasCongregacoes}`, faltando: false })
  }

  if (p.controlaPresenca) linhas.push({ texto: 'Check-in ativado', faltando: false })

  return linhas
}

export function PreviaEvento(props: PreviaEventoProps) {
  const dataCurta = formatarDataCurta(props.inicioData)
  const linhaQuando = [dataCurta, props.inicioHora, props.localResumo]
    .filter((x) => x && x.trim() !== '')
    .join(' · ')

  const regras = resumirRegras(props)

  return (
    <div className={styles.previa}>
      <span className={styles.rotulo}>Prévia</span>

      <div className={styles.card}>
        <div className={styles.thumb}>
          {props.fotoId ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={`/api/fotos/${props.fotoId}?tamanho=thumb`} alt="" className={styles.thumbImg} />
          ) : (
            <CalendarClock size={20} aria-hidden="true" />
          )}
        </div>
        <div className={styles.info}>
          <span className={props.titulo ? styles.titulo : styles.tituloVazio}>
            {props.titulo || 'Sem título'}
          </span>
          {linhaQuando && <span className={styles.quando}>{linhaQuando}</span>}
          {props.tipo && <span className={styles.tipo}>{props.tipo}</span>}
        </div>
      </div>

      <ul className={styles.regras}>
        {regras.map((r, i) => (
          <li key={i} className={r.faltando ? styles.regraFaltando : undefined}>
            {r.texto}
          </li>
        ))}
      </ul>
    </div>
  )
}
```

Nota sobre a URL da foto: o resto do front serve foto por `/api/fotos/{id}?tamanho=...` (proxy same-origin do Next). Confirmar o caminho exato conferindo um uso existente (ex.: `grep -rn "fotos/" frontend/src/components/common/UploadFoto`) e alinhar antes de fechar a task se divergir.

- [ ] **Step 2: Criar `PreviaEvento.module.css`**

```css
.previa {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px;
  border: 1px solid hsl(var(--cat-eventos) / 0.3);
  border-radius: var(--radius-lg);
  background: hsl(var(--cat-eventos) / 0.04);
}

.rotulo {
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-bold);
  letter-spacing: 1.2px;
  text-transform: uppercase;
  color: var(--color-text-secondary);
}

.card {
  display: flex;
  gap: 12px;
  align-items: center;
}
.thumb {
  width: 64px;
  height: 48px;
  flex-shrink: 0;
  border-radius: var(--radius-md);
  background: var(--color-bg-white);
  border: 1px solid var(--color-border-subtle);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-text-muted);
  overflow: hidden;
}
.thumbImg { width: 100%; height: 100%; object-fit: cover; }

.info { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.titulo {
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tituloVazio {
  font-weight: var(--font-weight-medium);
  color: var(--color-text-muted);
}
.quando { font-size: var(--font-size-sm); color: var(--color-text-muted); }
.tipo { font-size: var(--font-size-xs); color: var(--color-text-muted); }

.regras {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin: 0;
  padding-left: 18px;
  list-style: disc;
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
  line-height: var(--line-height-relaxed);
}
.regraFaltando {
  color: var(--color-danger);
  font-weight: var(--font-weight-medium);
  list-style: none;
  margin-left: -18px;
}

@media (max-width: 380px) {
  .card { flex-direction: column; align-items: flex-start; }
}
```

- [ ] **Step 3: Verificar build e lint**

```bash
cd frontend && npm run lint && npm run build
```
Esperado: sem erro. Componente ainda não usado.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/module/eventos/PreviaEvento.tsx frontend/src/components/module/eventos/PreviaEvento.module.css
git commit -m "feat(front): componente PreviaEvento (prévia ao vivo do evento)

Mini-card + regras em português (resumirRegras) montadas a partir dos
valores do form. Ainda não renderizado no EventoForm.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01PgEuCSiVzUaTXWAUfwHjg5"
```

---

## Task 5: `EventoForm` — coluna única, seções reordenadas, blocos recolhíveis, textos

**Files:**
- Modify: `frontend/src/components/module/eventos/EventoForm.tsx`
- Modify: `frontend/src/components/module/eventos/EventoForm.module.css`

**Interfaces:**
- Consumes: `BlocoRecolhivel` de `@/components/common/BlocoRecolhivel/BlocoRecolhivel` (Task 2).
- Produces: `EventoForm` com o novo layout. O botão salvar continua com o `disabled` de hoje nesta task (só a Task 6 troca isso) — mantém a task testável de forma independente.

Contexto do arquivo hoje (`EventoForm.tsx`): a `<form>` contém `<div className={styles.colunas}>` com `.colunaEsquerda` (seções "Informações do evento" + "Local") e `.colunaDireita` (seção `.secaoData` com "Data e horário" + recorrência + imagem + `.infoBox`, depois "Organização", "Para quem é", "Inscrições"). Fora do grid: `.blocoFinal` com "Campos personalizados" (dentro de `{requerInscricao && <Revelar>}`), erros e `.acoes`.

- [ ] **Step 1: Trocar o grid de 2 colunas por coluna única (CSS)**

Em `EventoForm.module.css`:

1. Substitui o bloco `.colunas` / `.colunaEsquerda` / `.colunaDireita` por:
```css
.colunas {
  display: flex;
  flex-direction: column;
  gap: 20px;
  max-width: 720px;
  margin: 0 auto;
}
```
(Mantém a classe `.colunas` no JSX pra minimizar diff; `.colunaEsquerda`/`.colunaDireita` deixam de existir — remover do CSS e do JSX no Step 3.)

2. Remove as classes mortas do CSS: `.colunaEsquerda`, `.colunaDireita`, `.secaoData` (e a regra `.secaoData .secaoHeader`), `.infoBox`, `.infoIcon`, `.infoText`.

3. Em `.blocoFinal`, troca `max-width` se houver / garante `max-width: 720px; margin: 0 auto;` pra alinhar com `.colunas`:
```css
.blocoFinal {
  display: flex;
  flex-direction: column;
  gap: 20px;
  max-width: 720px;
  margin: 24px auto 0;
}
```

4. No `@media (max-width: 1024px)` remove as regras de `.colunas { grid-template-columns: 1fr }` e `.colunaDireita { position: static }` (não há mais grid nem sticky). Se o media query ficar vazio, remove ele.

5. No `@media (max-width: 767px)`: troca `.colunaEsquerda, .colunaDireita, .blocoFinal { gap: 16px; }` por `.colunas, .blocoFinal { gap: 16px; }`. Remove a linha `.secao { box-shadow: ... }` só se quiser — pode manter. Mantém o resto.

- [ ] **Step 2: Trocar o fundo creme da seção de data**

No JSX, a seção "Data e horário" usa `<section className={styles.secaoData}>`. Troca pra `<section className={styles.secao}>`. Some a distinção visual — todas as seções ficam iguais.

- [ ] **Step 3: Reordenar o JSX em coluna única com as 4 seções + blocos recolhíveis**

Reescreve o corpo da `<form>` (o `<div className={styles.colunas}>` e o `<div className={styles.blocoFinal}>`) para esta ordem. **Todo o conteúdo interno de cada seção é o que já existe hoje — só muda o agrupamento e a ordem.** Import novo no topo:
```tsx
import { BlocoRecolhivel } from '@/components/common/BlocoRecolhivel/BlocoRecolhivel'
```

Estrutura nova dentro de `<div className={styles.colunas}>`:

**Seção 1 — "Sobre o evento"** (`<section className={styles.secao}>`, header com `<FileText size={20} />`):
- `<Input id="titulo" label="TÍTULO DO EVENTO*" ... />` (igual hoje)
- o `<div><InputComSugestoes id="tipo" label="TIPO DO EVENTO" ... /></div>` (igual hoje)
- o `<div className={styles.campoTextarea}>` da descrição (igual hoje)
- a `<div className={styles.imagemWrap}>` do `<UploadFoto ... formato="banner" />` (movida pra cá; hoje está na seção de data). Mantém o `<span className={styles.labelData}>IMAGEM DO EVENTO</span>` e a lógica do `onChange` (upload imediato em edição) exatamente como está.

**Seção 2 — "Quando e onde"** (`<section className={styles.secao}>`, header com `<CalendarClock size={20} />`):
- os dois `<div className={styles.grupoData}>` de INÍCIO* e TÉRMINO (iguais hoje, incluindo os `<CampoData>` e os inputs de hora)
- a `<section>` de "Local" de hoje **vira só o conteúdo** — coloca o `<SeletorLocal ... />` (com todas as props atuais) direto aqui, precedido de `<span className={styles.labelData}>LOCAL</span>`. Remove o `<section>` e o header próprio de "Local".
- o bloco de recorrência (`{!ehEdicao && ...}`) passa a ser envolvido por `<BlocoRecolhivel>`:
```tsx
{!ehEdicao && (
  <BlocoRecolhivel
    id="repetir"
    titulo="Repetir este evento"
    descricao="As próximas datas entram na agenda sozinhas."
    icone={<Repeat size={18} />}
  >
    {/* O MESMO conteúdo do <Revelar> de recorrência de hoje, MAS sem o
        toggle "Repetir" (o próprio BlocoRecolhivel é o gatilho). Ao abrir
        o bloco, tratar como repetir=true: no onClick do cabeçalho não dá
        pra saber — então usar um efeito: quando o bloco abre, setValue
        ('repetir', true); ao fechar, setValue('repetir', false). */}
  </BlocoRecolhivel>
)}
```
Detalhe importante da recorrência: hoje `repetir` é um `watch('repetir')` ligado a um `<input type="checkbox" {...register('repetir')} />`. Com o `BlocoRecolhivel` no lugar do toggle, o form precisa saber que "bloco aberto = repetir". Solução: manter o campo `repetir` no schema, e no `BlocoRecolhivel` da recorrência passar a controlar via um wrapper pequeno **dentro do EventoForm**:
```tsx
{!ehEdicao && (
  <BlocoRepetir
    aberto={!!repetir}
    onToggle={(v) => {
      setValue('repetir', v, { shouldValidate: true })
      if (!v) {
        // limpa os campos de recorrência ao desligar (evita validação fantasma)
        setValue('recorrenciaFrequencia', undefined)
        setValue('recorrenciaDiasSemana', [])
        setValue('recorrenciaFimTipo', 'NUNCA')
      }
    }}
  >
    {/* conteúdo de recorrência */}
  </BlocoRepetir>
)}
```
onde `BlocoRepetir` é um wrapper local (definido no mesmo arquivo, acima de `EventoForm`) que renderiza `<BlocoRecolhivel>` e sincroniza:
```tsx
function BlocoRepetir({
  aberto, onToggle, children,
}: { aberto: boolean; onToggle: (v: boolean) => void; children: React.ReactNode }) {
  // BlocoRecolhivel tem estado próprio; aqui a fonte da verdade é o form.
  // Usamos a key pra remontar quando `aberto` muda por fora (ex.: erro que abre).
  return (
    <div
      onClickCapture={(e) => {
        // clique no cabeçalho do BlocoRecolhivel: alterna o form também.
        const alvo = e.target as HTMLElement
        if (alvo.closest('button[aria-expanded]')) {
          onToggle(!aberto)
        }
      }}
    >
      <BlocoRecolhivel
        id="repetir"
        titulo="Repetir este evento"
        descricao="As próximas datas entram na agenda sozinhas."
        icone={<Repeat size={18} />}
        defaultAberto={aberto}
        key={aberto ? 'aberto' : 'fechado'}
      >
        {children}
      </BlocoRecolhivel>
    </div>
  )
}
```
Isso mantém `EventoForm` como fonte da verdade e reaproveita o `BlocoRecolhivel` sem alterá-lo. O campo `repetir` continua no `register` do schema mas **sem** o `<input type="checkbox">` visível — remove o `<label className={styles.toggleRow}>` de "Repetir" que existe hoje.

**Seção 3 — "Organização"** (`<section className={styles.secao}>`, header com `<UserCog size={20} />`):
- `<SeletorResponsavel ... />` (igual hoje)
- o `{temFamilia && (<label className={styles.toggleRow}> ... "Apenas minha igreja" ...)}` — troca o título para `Só minha igreja` e a descrição para `Não mostra este evento para {concordar(congregacao.genero, 'as')} outras {congregacao.plural.toLowerCase()}.` (o `concordar`/`congregacao` já vêm de `useRotulos()` no componente).

**Bloco recolhível "Restringir quem pode participar"** (fora de `<section>`, direto no `.colunas`, depois de Organização):
```tsx
<BlocoRecolhivel
  id="restringir-publico"
  titulo="Restringir quem pode participar"
  descricao="Idade, estado civil, sexo ou só membros"
  icone={<Users size={18} />}
  defaultAberto={
    !!exclusivoMembros || idadeMinAtual != null || idadeMaxAtual != null ||
    restricaoEstadoCivilAtual != null || restricaoSexoAtual != null
  }
>
  <BlocoParaQuemE
    /* MESMAS props de hoje */
  />
</BlocoRecolhivel>
```
Remove a `<section>` "Para quem é" com seu header próprio — o `BlocoRecolhivel` substitui.

**Seção 5 — "Inscrições"** (`<section className={styles.secao}>`, header com `<Ticket size={20} />`):
- o `<label className={styles.toggleRow}>` do `requerInscricao` — troca título para `Exigir inscrição` e descrição para `Participantes se inscrevem antes. Você controla vagas, prazo e valor.`
- o `{requerInscricao && <Revelar className={styles.campos}>}` com: vagas (hint → `Vazio = sem limite de vagas.`), o `grupoData` de "Inscrições até" (hint → `Vazio = aceita inscrição até o evento começar.`), o `<Revelar>` da política de cancelamento (hint → `Antes do prazo, cancelar é sempre livre e com reembolso.`), o `grupoData` "TIPO DE INSCRIÇÃO" (segmentado Gratuito/Pago), o `<label className={styles.toggleRow}>` do `controlaPresenca` — troca título para `Check-in`, descrição para `Marque quem chegou no dia e veja quantos vieram.`, mantém o `<ClipboardCheck size={16} />` — e o `{tipoInscricao === 'PAGO' && <Revelar>}` do preço (hint → `Cobrado na hora da inscrição.`).
- **dentro** do mesmo `<Revelar>` de `requerInscricao`, no fim, o painel de campos personalizados vira recolhível:
```tsx
<BlocoRecolhivel
  id="campos-personalizados"
  titulo="Campos personalizados"
  descricao="Perguntas extras no formulário de inscrição"
  icone={<ClipboardCheck size={18} />}
>
  <CamposPersonalizadosPainel ref={camposPersonalizadosRef} eventoId={eventoId} />
</BlocoRecolhivel>
```
Remove o `.blocoFinal` > `{requerInscricao && <Revelar><section>...Campos personalizados...}` de hoje. O `camposPersonalizadosRef` e o `useEffect` que registra `registrarSalvarCamposPersonalizados` **continuam iguais** — só o lugar de render muda.

**Fim do form** (`<div className={styles.blocoFinal}>`, agora só com erros + ações):
- `{erroGeral && ...}` e `{erroValidacao && ...}` (iguais)
- as `.acoes` com o `<Button type="submit">` e o link Cancelar (o `disabled` **não muda nesta task** — Task 6)
- os 3 modais (`ModalImpactoRestricao`, `ModalImpactoMudancaPreco`, `ModalEscopoEdicaoEvento`) e o `<OverlayCarregando>` — iguais, ficam no fim da `<form>`.

- [ ] **Step 4: Remover o `<Info>` import se ficou órfão**

Depois de tirar a `.infoBox` "O evento aparecerá na agenda…", conferir se `Info` de `lucide-react` ainda é usado no arquivo. Se não, remover do import da linha 5.

- [ ] **Step 5: Verificar build, lint e manual**

```bash
cd frontend && npm run lint && npm run build && npm run dev
```
Manual (em `http://localhost:3000/eventos/cadastrar` e num `/eventos/<id>`):
- Form aparece em coluna única, centralizado, ~720px, sem fundo creme, sem caixa azul "aparecerá na agenda".
- 4 seções na ordem: Sobre o evento / Quando e onde / Organização / Inscrições. "Restringir quem pode participar" entre Organização e Inscrições.
- "Repetir este evento" fechado por padrão no cadastro; abrir → campos de recorrência aparecem; preencher semanal + dias + salvar → série criada.
- "Restringir quem pode participar" fechado; abrir, marcar "só membros" → salvar OK. Editar um evento que já tem restrição → bloco abre expandido.
- "Exigir inscrição" ON → vagas/prazo/tipo/preço/Check-in aparecem; "Campos personalizados" recolhido dentro; abrir, adicionar um campo, salvar → campo persiste.
- Textos novos conferem com a tabela.
- Mobile (viewport iPhone + Android): sem scroll horizontal, cabeçalhos dos blocos recolhíveis tocáveis (≥44px), tudo empilha.
- `prefers-reduced-motion` on → blocos abrem sem animação.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components/module/eventos/EventoForm.tsx frontend/src/components/module/eventos/EventoForm.module.css
git commit -m "feat(front): form de evento em coluna única + blocos recolhíveis

Reorganiza em 4 seções (Sobre o evento / Quando e onde / Organização /
Inscrições) numa coluna só, igual no PC e no mobile. Recorrência,
'restringir público' e campos personalizados viram blocos recolhíveis —
fechados por padrão. Renomeia toggles: 'Exigir inscrição' e 'Check-in'.
Encurta hints, remove a caixa 'aparecerá na agenda' e o fundo creme.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01PgEuCSiVzUaTXWAUfwHjg5"
```

---

## Task 6: `EventoForm` — botão sempre ativo, rolagem até o erro, prévia ao vivo

**Files:**
- Modify: `frontend/src/components/module/eventos/EventoForm.tsx`

**Interfaces:**
- Consumes: `useRolarParaErro` de `@/hooks/forms/useRolarParaErro` (Task 1); `PreviaEvento` + `PreviaEventoProps` de `./PreviaEvento` (Task 4).
- Produces: comportamento final da spec — botão `disabled` só durante operação em curso; submit inválido rola até o erro; evento pago sem conta conectada vira erro de campo (não trava); `<PreviaEvento>` renderizada acima das ações.

- [ ] **Step 1: Ref do form + ligar o hook**

No topo de `EventoForm`, adiciona:
```tsx
const formRef = useRef<HTMLFormElement>(null)
const { rolarParaErro } = useRolarParaErro(formRef)
```
(`useRef` já é importado; senão adiciona.) Põe `ref={formRef}` na `<form>`.

- [ ] **Step 2: Chamar `rolarParaErro` no callback de erro do submit**

Troca o `onSubmit` da `<form>`:
```tsx
<form
  ref={formRef}
  className={styles.form}
  onSubmit={(e) => handleSubmit(
    (data) => { setErroValidacao(null); onSubmit(data) },
    () => {
      setErroValidacao('Faltou preencher um campo — te levei até ele.')
      rolarParaErro()
    },
  )(e)}
>
```

- [ ] **Step 3: Botão salvar sempre ativo**

Troca o `disabled` do `<Button type="submit">`:
```tsx
disabled={isLoading || isVerificandoImpacto}
```
(Sai `isFormIncomplete` e `precisaConectarContaPagamento`.) Remove a prop `isFormIncomplete` do destructuring de `props` e do `type EventoFormProps` se não for mais usada em nenhum outro lugar do arquivo (é usada só no `disabled` hoje — conferir e remover do tipo + do `props`). Deixa `isFormIncomplete` no retorno de `useEventoForm`/`useAppForm` (outros forms usam) — só o `EventoForm` para de receber/usar.

- [ ] **Step 4: Guard de evento pago sem conta conectada**

Ainda existe `const precisaConectarContaPagamento = requerInscricao && tipoInscricao === 'PAGO' && !!contaPagamento && !contaPagamento.conectada`. Mantém a const (usada no aviso). No handler de submit **válido**, antes de `onSubmit(data)`:
```tsx
(data) => {
  setErroValidacao(null)
  if (precisaConectarContaPagamento) {
    props.setError('preco', {
      type: 'manual',
      message: 'Conecte uma conta de recebimento antes de publicar um evento pago.',
    })
    setErroValidacao('Faltou preencher um campo — te levei até ele.')
    rolarParaErro()
    return
  }
  onSubmit(data)
},
```
(`setError` vem de `props` / `useFormReturn` — confirmar o nome; é `setError`.) Isso faz o `<Input id="preco" error={errors.preco?.message}>` mostrar a mensagem, que tem `data-campo-erro` (Task 3), então o `rolarParaErro` leva até ele.

Além disso, adiciona `data-campo-erro` no `<div className={styles.avisoContaPagamento}>` (o aviso amarelo) pra cobrir o caso de o campo preço estar vazio E a conta não conectada — o primeiro `[data-campo-erro]` no DOM ainda será o do campo preço (vem antes), então o scroll para no lugar certo de qualquer forma; o marcador no aviso é rede de segurança.

- [ ] **Step 5: Montar `camposFaltando` e `localResumo`**

Antes do `return`, dentro de `EventoForm`:
```tsx
const { touchedFields, isSubmitted } = props.formState
const rotulosRequired: Record<string, string> = {
  titulo: 'título',
  inicioData: 'data de início',
  inicioHora: 'horário de início',
}
const camposFaltando = Object.entries(rotulosRequired)
  .filter(([campo]) => {
    const valor = watch(campo as 'titulo')
    const vazio = valor == null || String(valor).trim() === ''
    const jaInteragiu = isSubmitted || touchedFields[campo as 'titulo']
    return vazio && jaInteragiu
  })
  .map(([, rotulo]) => rotulo)

const localResumo =
  novoLocalAtual?.nome?.trim() ||
  localTextoAtual?.trim() ||
  enderecoLocalAtual?.cidade?.trim() ||
  '' // localId cadastrado: sem nome à mão aqui; deixa vazio (a prévia só omite a linha)
```
Nota: se quiser resolver o nome do `localId` cadastrado, dá pra puxar do `SeletorLocal` no futuro; para v1, omitir é aceitável (a linha "quando" só perde o "· Local X").

- [ ] **Step 6: Renderizar `<PreviaEvento>` acima das ações**

Import:
```tsx
import { PreviaEvento } from './PreviaEvento'
```
No `.blocoFinal`, logo antes de `{erroGeral && ...}`:
```tsx
<PreviaEvento
  titulo={watch('titulo') as string}
  tipo={tipoAtual}
  inicioData={inicioData}
  inicioHora={watch('inicioHora') as string}
  fimData={fimData}
  localResumo={localResumo}
  fotoId={fotoIdAtual}
  requerInscricao={!!requerInscricao}
  tipoInscricao={(tipoInscricao as 'GRATUITO' | 'PAGO') ?? 'GRATUITO'}
  preco={preco}
  vagas={vagasAtual}
  inscricoesAteData={watch('inscricoesAteData') as string}
  exclusivoMembros={!!exclusivoMembros}
  idadeMin={idadeMinAtual}
  idadeMax={idadeMaxAtual}
  restricaoEstadoCivil={restricaoEstadoCivilAtual}
  restricaoSexo={restricaoSexoAtual}
  restritoPropriaIgreja={!!watch('restritoPropriaIgreja')}
  temFamilia={temFamilia}
  rotuloOutrasCongregacoes={`${concordar(congregacao.genero, 'as')} outras ${congregacao.plural.toLowerCase()}`}
  controlaPresenca={!!watch('controlaPresenca')}
  camposFaltando={camposFaltando}
/>
```

- [ ] **Step 7: Verificar build, lint e manual**

```bash
cd frontend && npm run lint && npm run build && npm run dev
```
Manual:
1. `/eventos/cadastrar`: digitar só o título, clicar **Salvar** (botão está ativo) → página rola suave até "data de início", campo foca, treme de leve, banner "Faltou preencher um campo — te levei até ele." Prévia mostra "Falta preencher: data e horário de início" em vermelho.
2. Preencher data/hora → prévia vira "Aberto a todos · sem inscrição" → Salvar → cria.
3. Ligar "Exigir inscrição", "Pago", preço R$ 30, sem conta conectada → Salvar → rola até o campo preço com a mensagem de conta; não cria.
4. Erro dentro de bloco recolhível fechado: forçar (ex.: idade máx < mín dentro de "Restringir…", fechar o bloco, salvar) → o bloco abre sozinho e rola até o campo.
5. Prévia atualiza ao vivo: mudar vagas, marcar Check-in, marcar "só membros" → linhas mudam na hora.
6. Editar evento existente → prévia reflete os valores carregados; salvar → mesmo modal de impacto/escopo de antes.
7. `prefers-reduced-motion` on → sem tremido, scroll instantâneo.
8. Mobile (iPhone + Android): prévia legível, sem overflow horizontal.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/components/module/eventos/EventoForm.tsx
git commit -m "feat(front): botão salvar sempre ativo + rolagem até o erro + prévia

Some o trava-botão por campo obrigatório e por conta de pagamento. Submit
incompleto rola suave até o primeiro campo com erro (abrindo o bloco
recolhível que o contém), foca e treme de leve. Evento pago sem conta
conectada vira erro do campo preço em vez de travar. PreviaEvento ao vivo
acima do botão.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01PgEuCSiVzUaTXWAUfwHjg5"
```

---

## Self-Review (feita pelo autor do plano)

**1. Cobertura da spec:**
- Coluna única PC=mobile → Task 5 Step 1/3. ✅
- 4 seções + ordem → Task 5 Step 3. ✅
- 3 blocos recolhíveis (Repetir / Restringir / Campos personalizados) → Task 5 Step 3 + Task 2 (componente). ✅
- `useRolarParaErro` reusável → Task 1. ✅
- `data-campo-erro` markers → Task 3. ✅
- `<BlocoRecolhivel>` + evento `domus:abrir-recolhivel` → Task 2. ✅
- `<PreviaEvento>` + `resumirRegras` → Task 4, render → Task 6 Step 6. ✅
- Botão sempre ativo → Task 6 Step 3. ✅
- Guard evento pago sem conta → Task 6 Step 4. ✅
- Todos os textos antes→depois → tabela no header + aplicados na Task 5/6. ✅
- Remover `.infoBox` / `.secaoData` → Task 5 Step 1/2/4. ✅
- `.tremido` global + reduced-motion → Task 1 Step 2. ✅
- Sem tocar backend / `useAppForm` / `validators` / páginas wrapper → respeitado (nenhuma task os modifica). ✅
- Checklist de validação manual → distribuída nas Tasks 5 Step 5 e 6 Step 7. ✅

**2. Placeholders:** nenhum "TBD"/"a definir"/"tratar edge cases" — todo step tem código ou comando concreto. Os dois pontos de "confirmar no arquivo" (caminho da URL de foto na Task 4; nome exato de `setError`/`formState` na Task 6) são verificações de 10s contra código existente, não trabalho em aberto.

**3. Consistência de tipos:**
- `useRolarParaErro(formRef: RefObject<HTMLFormElement | null>)` — mesma assinatura na Task 1 (produz) e Task 6 Step 1 (consome). ✅
- `domus:abrir-recolhivel` com `detail: { id: string }` — disparado na Task 1, escutado na Task 2. ✅
- `data-recolhivel` + `data-id` no wrapper — escrito na Task 2 (`data-recolhivel data-id={id}`), lido na Task 1 (`[data-recolhivel][hidden]` + `getAttribute('data-id')`). ✅
- `data-campo-erro` — escrito na Task 3, lido na Task 1 (`[data-campo-erro]`). ✅
- `PreviaEventoProps` — definido na Task 4, preenchido campo a campo na Task 6 Step 6; nomes conferem (`rotuloOutrasCongregacoes`, `camposFaltando`, `localResumo`, `tipoInscricao`). ✅
- `BlocoRecolhivelProps` (`id`, `titulo`, `descricao`, `icone`, `defaultAberto`, `children`) — definido na Task 2, usado na Task 5 com exatamente essas props. ✅
