# Redesign do Wizard de Cadastro — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Unificar os 3 passos do `/cadastro` num wizard coeso — shell único, painel esquerdo persistente, formulário trocando com transição direcional, passo 3 redesenhado com logo Domus.

**Architecture:** `page.tsx` fica fino e delega pro `<CadastroShell>`, que compõe `<PainelWizard>` (identidade + progresso, persistente) e `<TrocaPasso>` (palco que anima a troca de cena). `Passo1`/`Passo2` viram só o miolo do formulário; `Passo3` é um componente novo. Sem mudança de backend, validação ou fluxo de dados — só apresentação.

**Tech Stack:** Next.js 16 (App Router), React 19, TypeScript, CSS Modules, react-hook-form + Zod (já existentes). Ícones lucide-react. `next/image`.

**Spec:** `backend/api/docs/superpowers/specs/2026-09-08-wizard-cadastro-redesign-design.md`

**Referência visual:** mockups aprovados em `.superpowers/brainstorm/1070157-1788902304/content/shell.html` e `mobile-transicoes.html` (git-ignored; abrir no navegador se precisar rever o visual).

## Global Constraints

- Só frontend. Nenhuma mudança em `src/services/auth.service.ts`, `src/lib/validators`, ou no backend.
- Toda animação tem bloco `@media (prefers-reduced-motion: reduce)` que a zera/torna instantânea.
- `100dvh` com `100vh` de fallback na linha anterior; `-webkit-backdrop-filter` sempre junto de `backdrop-filter`.
- Sem confete / emoji de festa no passo 3. Tom sóbrio e profissional.
- Nenhum literal de passo solto: passo é `1 | 2 | 3`; direção é `1 | -1`.
- Não há testes de frontend no projeto — cada task fecha com `npx tsc --noEmit`, `npx eslint <arquivos>` e `npx next build` passando, e commit. O teste manual completo está na Task 4.
- Respostas e comentários de código em português brasileiro com acentuação.
- Rodar comandos a partir de `frontend/`.
- Commit trailers: `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>` e `Claude-Session: https://claude.ai/code/session_01PgEuCSiVzUaTXWAUfwHjg5`.

---

## File Structure

**Novos** (todos em `frontend/src/app/(auth)/cadastro/`):
- `TrocaPasso.tsx` + `TrocaPasso.module.css` — transição direcional entre cenas.
- `PainelWizard.tsx` + `PainelWizard.module.css` — painel esquerdo (desktop) / barra de topo (mobile); absorve `SanctuaryPanel` e `ProgressIndicator`.
- `Passo3.tsx` + `Passo3.module.css` — tela de boas-vindas redesenhada.
- `CadastroShell.tsx` + `CadastroShell.module.css` — o card unificado; fundo da página; action bar compartilhada.

**Modificados:**
- `frontend/src/app/(auth)/cadastro/page.tsx` — fica fino.
- `frontend/src/app/(auth)/cadastro/Passo1.tsx` + `Passo1.module.css` — vira miolo (perde header e action bar próprios).
- `frontend/src/app/(auth)/cadastro/Passo2.tsx` + `Passo2.module.css` — idem.
- `frontend/src/hooks/auth/UseRegistrarIgreja.ts` — expõe `direcaoPasso`; chama `marcarBoasVindasVista()` no sucesso.

**Removidos:**
- `frontend/src/app/(auth)/cadastro/SanctuaryPanel.tsx` + `.module.css`
- `frontend/src/app/(auth)/cadastro/ProgressIndicator.tsx` + `.module.css`
- `frontend/src/app/(auth)/cadastro/Page.module.css`

**Intocados:** `PasswordStrengthIndicator.*`, `SecurityFooter.*`.

---

## Task 1: `TrocaPasso` — transição direcional entre cenas

**Files:**
- Create: `frontend/src/app/(auth)/cadastro/TrocaPasso.tsx`
- Create: `frontend/src/app/(auth)/cadastro/TrocaPasso.module.css`

**Interfaces:**
- Consumes: nada.
- Produces:
  ```ts
  export function TrocaPasso(props: {
    passo: 1 | 2 | 3          // qual cena está ativa
    direcao: 1 | -1           // 1 = avançou (desliza p/ esquerda), -1 = voltou
    children: React.ReactNode // a cena já resolvida pelo pai
  }): JSX.Element
  ```
  Consumido pela Task 4 (`CadastroShell`).

**Contexto:** espelha o padrão do `frontend/src/components/common/TrocaCena/TrocaCena.tsx` (ler antes), mas horizontal e direcional. Regras que valem: ajuste de estado no corpo do render ao mudar a prop (não `setState` em `useEffect` — o eslint `react-hooks/set-state-in-effect` barra); a cena que sai continua montada até o fim da animação (sem "piscada"); `useLayoutEffect` só pra medir/animar altura.

- [ ] **Step 1: Criar `TrocaPasso.tsx`**

```tsx
'use client'

import { useLayoutEffect, useRef, useState, type ReactNode } from 'react'
import styles from './TrocaPasso.module.css'

const SAIDA_MS = 340

interface Camada {
  passo: 1 | 2 | 3
  no: ReactNode
  entrando: -1 | 1 | 0   // -1 entra da esquerda, 1 entra da direita, 0 = já assentada
}

/**
 * Troca entre as cenas do wizard de cadastro. Direcional: avançar desliza a cena atual
 * pra esquerda e traz a nova da direita; voltar faz o inverso. A altura do palco acompanha
 * a cena nova. Anima só quando `passo` muda. Espelha o padrão do <TrocaCena>, mas horizontal.
 */
export function TrocaPasso({ passo, direcao, children }: { passo: 1 | 2 | 3; direcao: 1 | -1; children: ReactNode }) {
  const [ativo, setAtivo] = useState<1 | 2 | 3>(passo)
  const [camadas, setCamadas] = useState<Camada[]>([{ passo, no: children, entrando: 0 }])
  const palcoRef = useRef<HTMLDivElement>(null)
  const alturaEstavel = useRef(0)

  // Ajuste de estado no render (estado derivado de prop): entra a cena nova, a antiga fica
  // na lista pra animar a saída.
  if (ativo !== passo) {
    setAtivo(passo)
    setCamadas((prev) => {
      const semDupe = prev.filter((c) => c.passo !== passo).map((c) => ({ ...c, entrando: 0 as const }))
      return [...semDupe, { passo, no: children, entrando: (direcao === 1 ? 1 : -1) }]
    })
  } else {
    // mesma cena, conteúdo pode ter mudado (erro de API apareceu): atualiza o nó da ativa.
    const ativaAtual = camadas.find((c) => c.passo === passo && c.entrando === 0)
    if (ativaAtual && ativaAtual.no !== children) {
      setCamadas((prev) => prev.map((c) => (c === ativaAtual ? { ...c, no: children } : c)))
    }
  }

  const emTransicao = camadas.length > 1

  useLayoutEffect(() => {
    if (!emTransicao && palcoRef.current) alturaEstavel.current = palcoRef.current.offsetHeight
  })

  useLayoutEffect(() => {
    if (!emTransicao) return
    const palco = palcoRef.current
    if (palco) {
      palco.style.height = `${alturaEstavel.current}px`
      void palco.offsetHeight // reflow: fixa o "de" antes de animar
      palco.style.height = `${palco.scrollHeight}px`
    }
    const t = window.setTimeout(() => {
      setCamadas((prev) => prev.filter((c) => c.passo === ativo).map((c) => ({ ...c, entrando: 0 as const })))
      if (palco) palco.style.height = ''
    }, SAIDA_MS)
    return () => window.clearTimeout(t)
    // reage só à troca de cena
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ativo])

  return (
    <div ref={palcoRef} className={styles.palco}>
      {camadas.map((c) => {
        const saindo = c.passo !== ativo
        const cls = [
          styles.cena,
          saindo ? (direcao === 1 ? styles.saiEsquerda : styles.saiDireita) : '',
          !saindo && c.entrando === 1 ? styles.entraDireita : '',
          !saindo && c.entrando === -1 ? styles.entraEsquerda : '',
        ].filter(Boolean).join(' ')
        return (
          <div key={c.passo} className={cls} aria-hidden={saindo}>
            {c.no}
          </div>
        )
      })}
    </div>
  )
}
```

- [ ] **Step 2: Criar `TrocaPasso.module.css`**

```css
.palco {
  position: relative;
  overflow: hidden;
  transition: height 0.34s cubic-bezier(0.22, 1, 0.36, 1);
}

.cena {
  width: 100%;
}

/* Cena que sai: fora do fluxo (não conta pra altura, que já mira a nova). */
.saiEsquerda,
.saiDireita {
  position: absolute;
  top: 0;
  left: 0;
  pointer-events: none;
}
.saiEsquerda { animation: saiEsq 0.34s cubic-bezier(0.4, 0, 0.6, 1) forwards; }
.saiDireita  { animation: saiDir 0.34s cubic-bezier(0.4, 0, 0.6, 1) forwards; }

.entraDireita { animation: entraDir 0.34s cubic-bezier(0.22, 1, 0.36, 1) 0.04s both; }
.entraEsquerda { animation: entraEsq 0.34s cubic-bezier(0.22, 1, 0.36, 1) 0.04s both; }

@keyframes saiEsq { to { opacity: 0; transform: translateX(-28px); } }
@keyframes saiDir { to { opacity: 0; transform: translateX(28px); } }
@keyframes entraDir { from { opacity: 0; transform: translateX(28px); } }
@keyframes entraEsq { from { opacity: 0; transform: translateX(-28px); } }

@keyframes soFade { from { opacity: 0; } }

@media (prefers-reduced-motion: reduce) {
  .palco { transition-duration: 0.01s; }
  .saiEsquerda, .saiDireita { animation: soFade 0.15s linear reverse forwards; }
  .entraDireita, .entraEsquerda { animation: soFade 0.15s linear both; }
}
```

- [ ] **Step 3: Verificar tipos, lint e build**

Run (de `frontend/`):
```bash
npx tsc --noEmit && npx eslint "src/app/(auth)/cadastro/TrocaPasso.tsx" && npx next build 2>&1 | grep -E "Compiled|error|Failed"
```
Esperado: `tsc` sem saída, eslint sem saída, build `✓ Compiled successfully`.

- [ ] **Step 4: Commit**

```bash
git add "frontend/src/app/(auth)/cadastro/TrocaPasso.tsx" "frontend/src/app/(auth)/cadastro/TrocaPasso.module.css"
git commit -m "feat(cadastro): TrocaPasso — transição direcional entre passos do wizard

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01PgEuCSiVzUaTXWAUfwHjg5"
```

---

## Task 2: `PainelWizard` — painel persistente (desktop) / barra de topo (mobile)

**Files:**
- Create: `frontend/src/app/(auth)/cadastro/PainelWizard.tsx`
- Create: `frontend/src/app/(auth)/cadastro/PainelWizard.module.css`

**Interfaces:**
- Consumes: nada.
- Produces:
  ```ts
  export const COPY_PASSO: Record<1 | 2 | 3, { rotulo: string; headline: string; texto: string; curto: string }>

  export function PainelWizard(props: {
    passoAtual: 1 | 2 | 3
    totalPassos: 2 | 3
    primeiroNome?: string   // pra headline do passo 3 ("Tudo certo, {primeiroNome}")
  }): JSX.Element
  ```
  Consumido pela Task 4.

**Contexto:** a foto é `/images/sanctuary.jpg` (já existe em `frontend/public/images/`). Verificar se `/images/logo2.png` existe (usada na sidebar); usar essa como logo. Breakpoint mobile: `@media (max-width: 900px)` (mesmo do `Page.module.css` atual).

- [ ] **Step 1: Criar `PainelWizard.tsx`**

```tsx
'use client'

import Image from 'next/image'
import styles from './PainelWizard.module.css'

export const COPY_PASSO: Record<1 | 2 | 3, { rotulo: string; headline: string; texto: string; curto: string }> = {
  1: {
    rotulo: 'Vamos começar',
    headline: 'Leva menos de 2 minutos',
    texto: 'Cadastre sua igreja e crie sua conta de administrador.',
    curto: 'Dados da igreja',
  },
  2: {
    rotulo: 'Falta pouco',
    headline: 'Sua conta de administrador',
    texto: 'Só mais alguns dados e o Domus é seu.',
    curto: 'Sua conta de admin',
  },
  3: {
    rotulo: 'Pronto',
    headline: 'Tudo certo',
    texto: 'Sua igreja foi criada. Escolha por onde começar.',
    curto: 'Tudo pronto',
  },
}

interface Props {
  passoAtual: 1 | 2 | 3
  totalPassos: 2 | 3
  primeiroNome?: string
}

export function PainelWizard({ passoAtual, totalPassos, primeiroNome }: Props) {
  const copy = COPY_PASSO[passoAtual]
  const headline =
    passoAtual === 3 && primeiroNome ? `Tudo certo, ${primeiroNome}` : copy.headline

  // Passos exibidos: no fluxo Google (totalPassos=2) o passo 3 é mostrado como "2 de 2".
  const passoExibido = totalPassos === 2 && passoAtual === 3 ? 2 : passoAtual
  const numeros = Array.from({ length: totalPassos }, (_, i) => i + 1)

  return (
    <aside
      className={`${styles.painel} ${passoAtual === 3 ? styles.celebra : ''}`}
      role="group"
      aria-label={`Passo ${passoExibido} de ${totalPassos}`}
    >
      <Image
        src="/images/sanctuary.jpg"
        alt=""
        aria-hidden="true"
        className={styles.foto}
        width={520}
        height={900}
        priority
      />
      <div className={styles.veu} aria-hidden="true" />

      <div className={styles.conteudo}>
        <div className={styles.marca}>
          <Image src="/images/logo2.png" alt="Domus" width={26} height={44} className={styles.logo} />
          <span className={styles.marcaNome}>DOMUS</span>
        </div>

        <div className={styles.meio}>
          <p className={styles.rotulo}>{copy.rotulo}</p>
          <h2 className={styles.headline}>{headline}</h2>
          <p className={styles.texto}>{copy.texto}</p>
        </div>

        {/* Progresso desktop: círculos numerados */}
        <ol className={styles.progresso}>
          {numeros.map((n) => {
            const feito = n < passoExibido
            const atual = n === passoExibido
            return (
              <li key={n} className={styles.progItem}>
                <span
                  className={`${styles.progNum} ${feito ? styles.progFeito : ''} ${atual ? styles.progAtual : ''}`}
                  aria-current={atual ? 'step' : undefined}
                >
                  {feito ? '✓' : n}
                </span>
                {n < totalPassos && (
                  <span className={styles.progLinha}>
                    <i style={{ width: feito ? '100%' : '0%' }} />
                  </span>
                )}
              </li>
            )
          })}
        </ol>
      </div>
    </aside>
  )
}
```

- [ ] **Step 2: Criar `PainelWizard.module.css`**

Basear no visual do mockup `shell.html` (painel esquerdo). Regras essenciais:

```css
.painel {
  position: relative;
  color: #fff;
  padding: 44px 40px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.foto {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  z-index: 0;
}

.veu {
  position: absolute;
  inset: 0;
  z-index: 1;
  background: linear-gradient(180deg, rgba(3, 17, 48, 0.35) 0%, rgba(3, 17, 48, 0.74) 100%);
}

.celebra::after {
  content: '';
  position: absolute;
  inset: 0;
  z-index: 1;
  background: radial-gradient(circle at 32% 34%, rgba(96, 165, 253, 0.45), transparent 62%);
  animation: brilho 2.6s ease-in-out infinite alternate;
}
@keyframes brilho { to { opacity: 0.4; } }

.conteudo {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  flex: 1;
  gap: 32px;
}

.marca { display: flex; align-items: center; gap: 10px; }
.marcaNome { font-size: 15px; font-weight: 700; letter-spacing: 0.16em; }

.rotulo {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: #93c5fd;
  margin-bottom: 10px;
}
.headline {
  font-size: 30px;
  line-height: 1.15;
  font-weight: 700;
  letter-spacing: -0.5px;
  max-width: 12ch;
}
.texto {
  margin-top: 14px;
  font-size: 14px;
  line-height: 1.6;
  color: rgba(255, 255, 255, 0.82);
  max-width: 32ch;
}

/* progresso — círculos */
.progresso { list-style: none; margin: 0; padding: 0; display: flex; align-items: center; gap: 10px; }
.progItem { display: flex; align-items: center; gap: 10px; }
.progNum {
  width: 26px; height: 26px; border-radius: 999px;
  border: 1.5px solid rgba(255, 255, 255, 0.4);
  display: grid; place-items: center;
  font-size: 12px; font-weight: 700;
  transition: background-color 0.35s, border-color 0.35s, box-shadow 0.35s;
}
.progFeito { background: #fff; color: #1d4ed8; border-color: #fff; }
.progAtual { border-color: #fff; box-shadow: 0 0 0 4px rgba(255, 255, 255, 0.18); }
.progLinha { width: 34px; height: 2px; background: rgba(255, 255, 255, 0.25); border-radius: 2px; overflow: hidden; }
.progLinha i { display: block; height: 100%; background: #fff; transition: width 0.5s cubic-bezier(0.22, 1, 0.36, 1); }

/* ---- Mobile: vira barra no topo do form, sem foto ---- */
@media (max-width: 900px) {
  .painel {
    padding: 16px 20px 14px;
    color: inherit;
    border-bottom: 1px solid var(--color-border-subtle, #eef2f7);
  }
  .foto, .veu, .celebra::after { display: none; }
  .conteudo { flex-direction: row; align-items: center; gap: 12px; }
  .marca { flex-shrink: 0; }
  .marcaNome { color: var(--color-text-dark, #1e293b); font-size: 12px; }
  /* no mobile a copy some (o passo já tem <h3> próprio no form); só marca + progresso em segmentos */
  .meio { display: none; }
  .progresso { flex: 1; gap: 6px; }
  .progItem { flex: 1; gap: 0; }
  .progNum { display: none; }
  .progLinha {
    width: auto; flex: 1; height: 4px; border-radius: 3px;
    background: var(--color-border, #e2e8f0);
  }
  /* último item não tem .progLinha — dar a ele um segmento próprio via ::after */
}

@media (prefers-reduced-motion: reduce) {
  .celebra::after { animation: none; }
  .progNum, .progLinha i { transition: none; }
}
```

> **Nota de implementação (mobile):** o `.progLinha` só renderiza entre círculos (`n < totalPassos`), então no mobile faltaria o segmento do último passo. Ajustar o JSX pra, no mobile, cada passo ter seu próprio segmento: renderizar sempre um `<span className={styles.progLinha}>` por passo (não só entre eles) e no **desktop** esconder o do último via CSS (`.progItem:last-child .progLinha { display: none }`). Preencher `width` do `i` com `feito || atual ? '100%' : '0%'` no mobile e `feito ? '100%' : '0%'` no desktop — usar uma classe no palco ou duas regras. Manter simples: renderizar segmento por passo sempre, `i` com `width: feito ? 100% : (atual ? 55% : 0)`; no desktop `.progItem:last-child .progLinha { display: none }`.

- [ ] **Step 3: Ajustar o JSX do progresso conforme a nota acima**

Trocar o bloco `{n < totalPassos && (...)}` por um segmento por passo:

```tsx
{numeros.map((n) => {
  const feito = n < passoExibido
  const atual = n === passoExibido
  return (
    <li key={n} className={styles.progItem}>
      <span
        className={`${styles.progNum} ${feito ? styles.progFeito : ''} ${atual ? styles.progAtual : ''}`}
        aria-current={atual ? 'step' : undefined}
      >
        {feito ? '✓' : n}
      </span>
      <span className={styles.progLinha}>
        <i style={{ width: feito ? '100%' : atual ? '55%' : '0%' }} />
      </span>
    </li>
  )
})}
```

E no CSS desktop adicionar: `.progItem:last-child .progLinha { display: none; }`.

- [ ] **Step 4: Verificar `/images/logo2.png` existe**

Run: `ls frontend/public/images/logo2.png frontend/public/images/sanctuary.jpg`
Esperado: os dois arquivos listados. Se `logo2.png` não existir, usar `/images/logo.png` (verificar qual existe) e ajustar `width`/`height` pra proporção real.

- [ ] **Step 5: Tipos, lint, build**

Run (de `frontend/`):
```bash
npx tsc --noEmit && npx eslint "src/app/(auth)/cadastro/PainelWizard.tsx" && npx next build 2>&1 | grep -E "Compiled|error|Failed"
```
Esperado: limpo, `✓ Compiled successfully`.

- [ ] **Step 6: Commit**

```bash
git add "frontend/src/app/(auth)/cadastro/PainelWizard.tsx" "frontend/src/app/(auth)/cadastro/PainelWizard.module.css"
git commit -m "feat(cadastro): PainelWizard — painel persistente + progresso (absorve SanctuaryPanel/ProgressIndicator)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01PgEuCSiVzUaTXWAUfwHjg5"
```

---

## Task 3: `Passo3` — tela de boas-vindas redesenhada

**Files:**
- Create: `frontend/src/app/(auth)/cadastro/Passo3.tsx`
- Create: `frontend/src/app/(auth)/cadastro/Passo3.module.css`

**Interfaces:**
- Consumes: nada.
- Produces:
  ```ts
  export function Passo3(props: {
    nomeIgreja: string
    nome: string
    irParaPessoas: () => void
    irParaPerfilIgreja: () => void
    irParaMeuPerfil: () => void
    irParaPainelInicial: () => void
  }): JSX.Element
  ```
  Consumido pela Task 4.

**Contexto:** substitui o bloco `passo === 3` do `page.tsx` atual (linhas ~59-122). Mantém os 3 atalhos e o "pular", muda o visual: logo Domus animada, check discreto, sem confete. Ícones: `Users`, `Building2`, `UserCog`, `ArrowRight`, `Check` do lucide-react (já usados no projeto).

- [ ] **Step 1: Criar `Passo3.tsx`**

```tsx
'use client'

import Image from 'next/image'
import { Users, Building2, UserCog, ArrowRight, Check } from 'lucide-react'
import styles from './Passo3.module.css'

interface Props {
  nomeIgreja: string
  nome: string
  irParaPessoas: () => void
  irParaPerfilIgreja: () => void
  irParaMeuPerfil: () => void
  irParaPainelInicial: () => void
}

export function Passo3({
  nomeIgreja, nome, irParaPessoas, irParaPerfilIgreja, irParaMeuPerfil, irParaPainelInicial,
}: Props) {
  const primeiroNome = nome?.trim().split(/\s+/)[0] ?? ''

  return (
    <div className={styles.container}>
      <Image src="/images/logo2.png" alt="Domus" width={40} height={66} className={styles.logo} />
      <span className={styles.check} aria-hidden="true"><Check size={16} strokeWidth={3} /></span>

      <h2 className={styles.titulo}>Igreja criada</h2>
      <p className={styles.sub}>
        A <strong>{nomeIgreja}</strong> já está no ar.
        {primeiroNome && <> Bem-vindo, <strong>{primeiroNome}</strong>.</>}
      </p>

      <span className={styles.comecar}>POR ONDE COMEÇAR?</span>

      <div className={styles.atalhos}>
        <button type="button" className={`${styles.atalho} ${styles.atalhoPrimario}`} onClick={irParaPessoas}>
          <span className={styles.atalhoIcone}><Users size={20} /></span>
          <span className={styles.atalhoTexto}>
            <strong>Cadastrar pessoas</strong>
            <span>Comece adicionando as pessoas da sua comunidade.</span>
          </span>
          <ArrowRight size={16} className={styles.atalhoSeta} />
        </button>

        <button type="button" className={styles.atalho} onClick={irParaPerfilIgreja}>
          <span className={styles.atalhoIcone}><Building2 size={20} /></span>
          <span className={styles.atalhoTexto}>
            <strong>Completar perfil da igreja</strong>
            <span>Adicione informações e personalize sua igreja.</span>
          </span>
          <ArrowRight size={16} className={styles.atalhoSeta} />
        </button>

        <button type="button" className={styles.atalho} onClick={irParaMeuPerfil}>
          <span className={styles.atalhoIcone}><UserCog size={20} /></span>
          <span className={styles.atalhoTexto}>
            <strong>Completar meu perfil</strong>
            <span>Adicione seus dados pessoais e de contato.</span>
          </span>
          <ArrowRight size={16} className={styles.atalhoSeta} />
        </button>
      </div>

      <button type="button" className={styles.pularLink} onClick={irParaPainelInicial}>
        Pular e ir para o painel inicial
      </button>
    </div>
  )
}
```

- [ ] **Step 2: Criar `Passo3.module.css`**

Reaproveitar as regras de `.atalho*` e `.pularLink` do `Page.module.css` atual (linhas ~254-325) — copiar como estão. Adicionar o novo topo com animações:

```css
.container {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 8px 4px;
}

.logo {
  width: auto;
  height: 56px;
  margin-bottom: 6px;
  animation: logoEntra 0.6s cubic-bezier(0.22, 1, 0.36, 1) both;
}
@keyframes logoEntra { from { opacity: 0; transform: scale(0.72); } }

.check {
  width: 26px;
  height: 26px;
  border-radius: 999px;
  background: var(--color-success-bg, #dcfce7);
  color: var(--color-success, #16a34a);
  display: grid;
  place-items: center;
  margin-bottom: 14px;
  animation: logoEntra 0.5s 0.2s cubic-bezier(0.22, 1, 0.36, 1) both;
}

.titulo { font-size: 24px; font-weight: 800; color: var(--color-text-dark, #0f172a); }
.sub {
  margin-top: 6px;
  font-size: 14px;
  color: var(--color-text-secondary, #475569);
  line-height: 1.5;
  max-width: 40ch;
}
.sub strong { color: var(--color-primary, #2563EB); font-weight: 600; }

.comecar {
  align-self: flex-start;
  margin-top: 28px;
  margin-bottom: 12px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.05em;
  color: var(--color-text-muted, #64748b);
}

.atalhos { width: 100%; display: flex; flex-direction: column; gap: 12px; margin-bottom: 24px; }

/* atalho — cascata de entrada */
.atalho {
  /* ...copiar as props visuais de .atalho do Page.module.css atual... */
  opacity: 0;
  transform: translateY(8px);
  animation: atalhoSobe 0.4s cubic-bezier(0.22, 1, 0.36, 1) forwards;
}
.atalho:nth-child(1) { animation-delay: 0.30s; }
.atalho:nth-child(2) { animation-delay: 0.40s; }
.atalho:nth-child(3) { animation-delay: 0.50s; }
@keyframes atalhoSobe { to { opacity: 1; transform: none; } }

/* ...demais regras .atalhoPrimario / .atalhoIcone / .atalhoTexto / .atalhoSeta / .pularLink
   copiadas literalmente de Page.module.css (linhas ~285-325)... */

@media (prefers-reduced-motion: reduce) {
  .logo, .check, .atalho { animation: none; opacity: 1; transform: none; }
}
```

> **Nota:** ao criar o CSS, abrir `Page.module.css` e colar as regras `.atalho`, `.atalhoPrimario`, `.atalhoIcone`, `.atalhoTexto`, `.atalhoSeta`, `.pularLink` (e os `@media (hover: hover)` delas) sem alterar — só adicionar a animação de cascata em `.atalho`. Trocar cores hardcoded (`#2563EB`, `#0f172a`, etc.) pelas variáveis CSS equivalentes quando existir (`--color-primary`, `--color-text-dark`); manter o hardcode se não houver variável.

- [ ] **Step 3: Tipos, lint, build**

Run (de `frontend/`):
```bash
npx tsc --noEmit && npx eslint "src/app/(auth)/cadastro/Passo3.tsx" && npx next build 2>&1 | grep -E "Compiled|error|Failed"
```
Esperado: limpo, `✓ Compiled successfully`.

- [ ] **Step 4: Commit**

```bash
git add "frontend/src/app/(auth)/cadastro/Passo3.tsx" "frontend/src/app/(auth)/cadastro/Passo3.module.css"
git commit -m "feat(cadastro): Passo3 redesenhado — logo Domus, check discreto, atalhos em cascata

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01PgEuCSiVzUaTXWAUfwHjg5"
```

---

## Task 4: `CadastroShell` + integração — hook, `Passo1`/`Passo2` miolo, `page.tsx`, deleções, teste manual

**Files:**
- Create: `frontend/src/app/(auth)/cadastro/CadastroShell.tsx`
- Create: `frontend/src/app/(auth)/cadastro/CadastroShell.module.css`
- Modify: `frontend/src/hooks/auth/UseRegistrarIgreja.ts`
- Modify: `frontend/src/app/(auth)/cadastro/page.tsx`
- Modify: `frontend/src/app/(auth)/cadastro/Passo1.tsx` + `Passo1.module.css`
- Modify: `frontend/src/app/(auth)/cadastro/Passo2.tsx` + `Passo2.module.css`
- Delete: `SanctuaryPanel.tsx` + `.module.css`, `ProgressIndicator.tsx` + `.module.css`, `Page.module.css`

**Interfaces:**
- Consumes:
  - `TrocaPasso` (Task 1): `{ passo: 1|2|3, direcao: 1|-1, children }`
  - `PainelWizard` + `COPY_PASSO` (Task 2): `{ passoAtual: 1|2|3, totalPassos: 2|3, primeiroNome? }`
  - `Passo3` (Task 3): `{ nomeIgreja, nome, irParaPessoas, irParaPerfilIgreja, irParaMeuPerfil, irParaPainelInicial }`
  - `marcarBoasVindasVista` de `@/lib/boasVindas`
- Produces: o wizard funcional. Fim da cadeia.

- [ ] **Step 1: `UseRegistrarIgreja.ts` — adicionar `direcaoPasso` e `marcarBoasVindasVista()`**

No topo, junto dos imports:
```ts
import { marcarBoasVindasVista } from "@/lib/boasVindas";
```

Adicionar estado (junto de `const [passo, setPasso] = useState<1 | 2 | 3>(1)`):
```ts
const [direcaoPasso, setDirecaoPasso] = useState<1 | -1>(1)
```

Em `irParaPasso2`:
```ts
const irParaPasso2 = (data: RegistrarIgrejaFormData1) => {
  setDataPasso1(data)
  setDirecaoPasso(1)
  setPasso(2)
}
```

Em `voltarParaPasso1`:
```ts
const voltarParaPasso1 = () => {
  setDirecaoPasso(-1)
  setPasso(1)
}
```

Em `onSubmit`, no bloco de sucesso (antes de `setPasso(3)`):
```ts
login(response)
marcarBoasVindasVista()
setDadosSucesso({ nome: response.nome, nomeIgreja: dataPasso1.nomeIgreja })
setDirecaoPasso(1)
setPasso(3)
```

E no bloco `if (codigo === 'CNPJ_DUPLICADO')`:
```ts
if (codigo === 'CNPJ_DUPLICADO') {
  setError('cnpj', { type: 'server', message: data?.message })
  setDirecaoPasso(-1)
  setPasso(1)
  return
}
```

Em `onSubmitGoogle`, no bloco de sucesso:
```ts
login(response)
marcarBoasVindasVista()
setDadosSucesso({ nome: response.nome, nomeIgreja: dataIgreja.nomeIgreja })
setDirecaoPasso(1)
setPasso(3)
```

Adicionar `direcaoPasso` ao objeto de retorno do hook.

- [ ] **Step 2: `Passo1.tsx` — virar miolo**

Remover: o `<header className={styles.header}>` (título/subtítulo — passam pro shell) e a `<div className={styles.actions}>` no final (a action bar vem do shell via classe compartilhada, mas Passo1 ainda renderiza os botões — ver Step 4). Manter: `<div className={styles.container}>` como wrapper, o botão Google, o divisor, os campos, o banner Google + checkbox de termos.

Estrutura nova do `return` (esqueleto):
```tsx
return (
  <div className={styles.container}>
    {modoGoogle ? (
      <div className={styles.googleBanner}>
        Cadastrando como <strong>{googleData!.nome}</strong> ({googleData!.email})
      </div>
    ) : (
      <>
        <div className={styles.googleWrap}>
          <GoogleLogin
            onSuccess={(cred) => { if (cred.credential) onGoogleAuth(cred.credential) }}
            onError={onGoogleError}
            text="signup_with"
            width="280"
          />
        </div>
        <div className={styles.divider}><span className={styles.dividerText}>OU PREENCHA MANUALMENTE</span></div>
      </>
    )}

    <form className={styles.form} onSubmit={handleSubmit(modoGoogle ? onSubmitGoogle : onAvancar)}>
      {/* ...os 4 Inputs iguais aos de hoje (nomeIgreja, row com cnpj+telefone, emailContato)... */}
      {/* ...o bloco termosWrapper quando modoGoogle... */}
      {erroGeral && <div className={styles.erroGeral}>{erroGeral}</div>}

      <div className={styles.acoes}>
        <Link href="/login" className={styles.voltar}>
          <ArrowLeft size={14} />
          <span>Voltar ao login</span>
        </Link>
        <Button
          type="submit"
          variant="primary"
          size="md"
          disabled={passo1Incompleto || isLoading || (modoGoogle && !aceitouTermosGoogle)}
          isLoading={modoGoogle && isLoading}
          loadingText="Cadastrando..."
        >
          {modoGoogle ? 'Concluir cadastro' : 'Próximo'}
          {!modoGoogle && <ArrowRight size={14} />}
        </Button>
      </div>
    </form>
  </div>
)
```

No CSS `Passo1.module.css`: remover `.header`, `.title`, `.subtitle`. Renomear `.actions` → `.acoes` OU deixar o shell definir `.acoes` globalmente — **decisão: cada passo define seu `.acoes`** com as mesmas props (`display: flex; justify-content: space-between; align-items: center; margin-top: 24px; padding-top: 20px; border-top: 1px solid var(--color-border-subtle);`). Definir `.voltar` (link discreto) igual em Passo1/Passo2/Passo3-pular.

- [ ] **Step 3: `Passo2.tsx` — virar miolo**

Remover o `<header className={styles.header}>` (linhas ~38-44). Manter o `<form>` com todos os campos. Trocar o botão "Criar conta" + link "Voltar" (hoje separados, botão full-width e link abaixo) por uma `.acoes` no mesmo padrão do Passo1:
```tsx
<div className={styles.acoes}>
  <button type="button" className={styles.voltar} onClick={onVoltar} disabled={isLoading}>
    <ArrowLeft size={14} />
    <span>Voltar</span>
  </button>
  <Button type="submit" variant="primary" size="md" isLoading={isLoading} disabled={passo2Incompleto || isLoading}>
    Criar conta
  </Button>
</div>
```
No CSS: remover `.header`, `.title`, `.subtitle`; adicionar `.acoes` e `.voltar` iguais aos do Passo1.

- [ ] **Step 4: Criar `CadastroShell.tsx`**

```tsx
'use client'

import { PainelWizard, COPY_PASSO } from './PainelWizard'
import { TrocaPasso } from './TrocaPasso'
import { Passo1 } from './Passo1'
import { Passo2 } from './Passo2'
import { Passo3 } from './Passo3'
import { SecurityFooter } from './SecurityFooter'
import styles from './CadastroShell.module.css'
import type { useRegistrarIgreja } from '@/hooks/auth/UseRegistrarIgreja'

type HookRetorno = ReturnType<typeof useRegistrarIgreja>

export function CadastroShell(h: HookRetorno) {
  const totalPassos: 2 | 3 = h.googleData ? 2 : 3
  const primeiroNome = h.dadosSucesso?.nome?.trim().split(/\s+/)[0]

  const tituloForm =
    h.passo === 1 ? 'Cadastre sua igreja'
      : h.passo === 2 ? 'Crie sua conta de administrador'
        : ''
  const subtituloForm =
    h.passo === 1 ? 'Comece pelos dados da comunidade.'
      : h.passo === 2 ? 'Seus dados pessoais para gerenciar o Domus.'
        : ''

  const cena =
    h.passo === 1 ? (
      <Passo1
        register={h.register} handleSubmit={h.handleSubmit} errors={h.errors}
        passo1Incompleto={h.passo1Incompleto} setValue={h.setValue} onAvancar={h.irParaPasso2}
        googleData={h.googleData} onGoogleAuth={h.onGoogleAuth} onGoogleError={h.onGoogleError}
        onSubmitGoogle={h.onSubmitGoogle} erroGeral={h.erroGeral} isLoading={h.isLoading}
        aceitouTermosGoogle={h.aceitouTermosGoogle} setAceitouTermosGoogle={h.setAceitouTermosGoogle}
      />
    ) : h.passo === 2 ? (
      <Passo2
        register={h.register2} handleSubmit={h.handleSubmit2} errors={h.errors2}
        passo2Incompleto={h.passo2Incompleto} watch={h.watch2} erroGeral={h.erroGeral}
        isLoading={h.isLoading} onSubmit={h.onSubmit} onVoltar={h.voltarParaPasso1}
      />
    ) : (
      <Passo3
        nomeIgreja={h.dadosSucesso?.nomeIgreja ?? ''} nome={h.dadosSucesso?.nome ?? ''}
        irParaPessoas={h.irParaPessoas} irParaPerfilIgreja={h.irParaPerfilIgreja}
        irParaMeuPerfil={h.irParaMeuPerfil} irParaPainelInicial={h.irParaPainelInicial}
      />
    )

  return (
    <div className={styles.page}>
      <div className={styles.shell}>
        <PainelWizard passoAtual={h.passo} totalPassos={totalPassos} primeiroNome={primeiroNome} />

        <div className={styles.formArea}>
          {/* rótulo do passo no mobile (o painel some) e no desktop (acima do form) */}
          <p className={styles.passoLabel}>
            Passo {totalPassos === 2 && h.passo === 3 ? 2 : h.passo} de {totalPassos}
            {' · '}{COPY_PASSO[h.passo].curto}
          </p>
          {tituloForm && (
            <div className={styles.formHead}>
              <h3 className={styles.formTitulo}>{tituloForm}</h3>
              <p className={styles.formSub}>{subtituloForm}</p>
            </div>
          )}

          <TrocaPasso passo={h.passo} direcao={h.direcaoPasso}>
            {cena}
          </TrocaPasso>

          {h.passo !== 3 && <SecurityFooter />}
        </div>
      </div>
    </div>
  )
}
```

> **Nota:** `HookRetorno = ReturnType<typeof useRegistrarIgreja>` evita duplicar a lista de props. Se o TS reclamar de `import type` circular, extrair uma `interface` explícita — mas tentar o `ReturnType` primeiro.

- [ ] **Step 5: Criar `CadastroShell.module.css`**

Basear no `shell.html`. Regras:

```css
.page {
  min-height: 100vh;
  min-height: 100dvh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px 16px;
  position: relative;
  overflow: hidden;
  background:
    radial-gradient(ellipse at top right, rgba(251, 207, 232, 0.4) 0%, transparent 50%),
    radial-gradient(ellipse at bottom left, rgba(147, 197, 253, 0.4) 0%, transparent 50%),
    radial-gradient(ellipse at center, rgba(196, 181, 253, 0.3) 0%, transparent 60%),
    linear-gradient(135deg, #faf8ff 0%, #e0e7ff 100%);
}

.shell {
  width: 100%;
  max-width: 1000px;
  min-width: 0;
  display: grid;
  grid-template-columns: 4.4fr 7fr;
  background: var(--color-bg-white, #fff);
  border-radius: 20px;
  overflow: hidden;
  box-shadow: var(--shadow-card-login);
  position: relative;
  z-index: 1;
  animation: shellEntra 0.34s cubic-bezier(0.16, 1, 0.3, 1);
}
@keyframes shellEntra { from { opacity: 0; transform: translateY(10px); } }

.formArea {
  min-width: 0;
  padding: 48px 52px;
  display: flex;
  flex-direction: column;
}

.passoLabel {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--color-text-muted, #94a3b8);
  margin-bottom: 18px;
}
.formHead { margin-bottom: 4px; }
.formTitulo { font-size: 22px; font-weight: 700; color: var(--color-text-dark, #0f172a); }
.formSub { font-size: 13px; color: var(--color-text-muted, #64748b); margin-top: 6px; }

@media (max-width: 900px) {
  .page { padding: 0; align-items: stretch; }
  .shell {
    grid-template-columns: 1fr;
    max-width: 100%;
    border-radius: 0;
    box-shadow: none;
    min-height: 100dvh;
  }
  .formArea { padding: 24px 20px calc(env(safe-area-inset-bottom) + 24px); flex: 1; }
  /* o painel (agora barra) fica no topo via ordem natural do grid */
  .passoLabel { display: none; } /* a barra do PainelWizard já mostra o progresso; o <h3> abaixo dá o contexto */
}

@media (max-width: 480px) {
  .formArea { padding: 20px 16px calc(env(safe-area-inset-bottom) + 20px); }
}

@media (prefers-reduced-motion: reduce) {
  .shell { animation: none; }
}
```

> **Decisão:** no desktop o `.passoLabel` aparece (contexto redundante com o painel, mas ajuda o scan). Se ficar poluído no teste manual, esconder no desktop também e manter só o `<h3>`. No mobile o `.passoLabel` some (a barra de segmentos + `<h3>` bastam).

- [ ] **Step 6: `page.tsx` — ficar fino**

```tsx
'use client'

import { useRegistrarIgreja } from '../../../hooks/auth/UseRegistrarIgreja'
import { CadastroShell } from './CadastroShell'

export default function CadastroPage() {
  const hook = useRegistrarIgreja()
  return <CadastroShell {...hook} />
}
```

- [ ] **Step 7: Deletar arquivos órfãos**

```bash
cd frontend
rm "src/app/(auth)/cadastro/SanctuaryPanel.tsx" "src/app/(auth)/cadastro/SanctuaryPanel.module.css"
rm "src/app/(auth)/cadastro/ProgressIndicator.tsx" "src/app/(auth)/cadastro/ProgressIndicator.module.css"
rm "src/app/(auth)/cadastro/Page.module.css"
grep -rn "SanctuaryPanel\|ProgressIndicator\|Page.module" "src/app/(auth)/cadastro/"
```
Esperado: o `grep` não retorna nada (nenhuma referência sobrou).

- [ ] **Step 8: Tipos, lint, build**

Run (de `frontend/`):
```bash
npx tsc --noEmit && npx eslint "src/app/(auth)/cadastro/" "src/hooks/auth/UseRegistrarIgreja.ts" && npx next build 2>&1 | grep -E "Compiled|error|Failed"
```
Esperado: tudo limpo, `✓ Compiled successfully`.

- [ ] **Step 9: Teste manual (rodar `npm run dev` em `frontend/`)**

Percorrer e confirmar cada item:

- [ ] **Nativo:** `/cadastro` → passo 1 preenchido → **Próximo** desliza pro passo 2 (cena vai pra esquerda, nova entra da direita; painel: rótulo "Falta pouco", círculo 2 vira atual, linha 1→2 preenche). → **Voltar** faz o inverso, dados do passo 1 preservados.
- [ ] **Nativo:** passo 2 preenchido → **Criar conta** → passo 3 desliza; painel ganha brilho; logo Domus escala e aparece; check discreto; 3 atalhos sobem em cascata.
- [ ] **Google:** passo 1 → clicar Google → banner "Cadastrando como X" + checkbox de termos aparece; progresso re-desenha pra "de 2" → **Concluir cadastro** → passo 3 (mostrado como "2 de 2").
- [ ] **Erro CNPJ_DUPLICADO:** (simular via CNPJ já cadastrado) → volta pro passo 1 (transição de "voltar") com erro no campo CNPJ.
- [ ] **Erro EMAIL_DUPLICADO:** fica no passo 2 com erro no campo e-mail.
- [ ] **Erro geral de API:** `erroGeral` aparece no rodapé da cena atual.
- [ ] **Mobile (DevTools iPhone + Android):** painel some, barra de segmentos no topo, `<h3>` dá o contexto, transições suaves, sem overflow horizontal, teclado não quebra o layout. Passo 3 centralizado, atalhos empilhados.
- [ ] **`prefers-reduced-motion` ligado** (DevTools → Rendering): sem slide, sem cascata, sem brilho — tudo instantâneo e funcional.
- [ ] **Pós-cadastro:** clicar "Cadastrar pessoas" leva pra `/pessoas`. Abrir o app de novo (F5 em `/inicio`) → a animação "curta" de boas-vindas do login **NÃO** toca (o `marcarBoasVindasVista()` do passo de sucesso já marcou).
- [ ] **Foco:** ao trocar de passo no desktop (>768px), o foco vai pro primeiro campo (ou `<h3>`) da cena nova, após a animação. No mobile o foco **não** é movido automaticamente.

> Se o item de foco automático não estiver implementado, adicionar no `CadastroShell` um `useEffect` que, ao mudar `h.passo` e `window.matchMedia('(min-width: 768px)').matches`, chama `.focus()` no primeiro `input`/`h3` do `.formArea` via ref, dentro de um `setTimeout(…, 360)`. Não usar `setState` — só `.focus()`.

- [ ] **Step 10: Commit**

```bash
cd frontend
git add "src/app/(auth)/cadastro/" "src/hooks/auth/UseRegistrarIgreja.ts"
git commit -m "feat(cadastro): shell unificado do wizard + integração

page.tsx delega pro <CadastroShell> (painel persistente + <TrocaPasso>).
Passo1/Passo2 viram miolo; Passo3 novo. Hook expõe direcaoPasso e marca
boas-vindas vista. SanctuaryPanel/ProgressIndicator/Page.module.css removidos.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01PgEuCSiVzUaTXWAUfwHjg5"
```

---

## Self-Review

**1. Spec coverage:**
- Shell unificado → Task 4 (`CadastroShell`). ✅
- Painel persistente (foto + progresso + copy que evolui) → Task 2. ✅
- Progresso: círculos no desktop, segmentos no mobile → Task 2 Step 2/3. ✅
- Transição direcional entre passos → Task 1. ✅
- Passo 3 redesenhado (logo, check, cascata, sem confete) → Task 3. ✅
- `useRegistrarIgreja`: `direcaoPasso` + `marcarBoasVindasVista()` → Task 4 Step 1. ✅
- Copy que evolui (tabela) → Task 2 (`COPY_PASSO`). ✅
- Fluxo Google (2 passos) → Task 4 Step 4 (`totalPassos`), Task 2 (`passoExibido`). ✅
- Casos de borda (CNPJ/e-mail duplicado, erro geral, voltar) → Task 4 Step 1 + Step 9. ✅
- Acessibilidade (aria, foco, reduced-motion) → Tasks 1/2/3 (reduced-motion), Task 4 Step 9 (foco). ✅
- CSS consolidado, deleções → Task 4 Steps 5/7. ✅
- Mobile → Tasks 2/4 (media queries) + Task 4 Step 9. ✅
- Sem backend, sem validação → Global Constraints. ✅

**2. Placeholder scan:** Task 3 Step 2 tem `/* ...copiar as props... */` — é uma instrução explícita de copiar regras existentes literalmente de um arquivo nomeado com linhas, não um "TODO" vago. Aceitável, mas o executor deve abrir `Page.module.css` e copiar de verdade. Task 2 Step 2 CSS mobile tem uma nota sobre o segmento do último passo — resolvida no Step 3. OK.

**3. Type consistency:**
- `passo`/`passoAtual`: `1 | 2 | 3` em TrocaPasso, PainelWizard, CadastroShell. ✅
- `direcao`/`direcaoPasso`: `1 | -1` em TrocaPasso e no hook. ✅
- `totalPassos`: `2 | 3` em PainelWizard e CadastroShell. ✅
- `COPY_PASSO`: exportado na Task 2, consumido na Task 4 (`.curto`). ✅
- `Passo3` props batem entre Task 3 (define) e Task 4 (chama). ✅
- `marcarBoasVindasVista` — assinatura `(): void` confirmada em `src/lib/boasVindas.ts`. ✅
