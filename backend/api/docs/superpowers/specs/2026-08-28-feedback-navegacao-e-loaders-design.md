# Feedback de navegação global + biblioteca de loaders + polimento do sidebar

> Spec de design. Data: 2026-08-28. Frontend (Next.js 16, App Router, CSS Modules).

> **Revisão 2026-08-28 (durante execução):** separados dois problemas distintos que antes
> eram um "modo" só. (1) **Navegação de rota** nunca bloqueia — o indicador é sempre a
> barra do topo (+ spinner no link do sidebar). O modo `overlay` saiu do
> `MODO_INDICADOR_NAV`, que agora é `'barra' | 'barra-e-link'` (default `'barra-e-link'`).
> (2) **Operação bloqueante** (pagar, excluir, submeter form) ganha um componente próprio
> `<OverlayCarregando>` (ex-`OverlayNav`), opt-in caso a caso — ver **Peça B2**.

## Problema

O app dá feedback de "carregando" de forma esparsa e inconsistente:

- Só existe **um** `loading.tsx` (na pasta `(app)`). Toda rota é `dynamic = 'force-dynamic'`
  (por causa do nonce da CSP), então **toda navegação bate no servidor** e tem latência
  real — mas na maioria das telas o usuário clica e não acontece nada visível até a
  próxima página aparecer.
- Os spinners existentes são ad-hoc: um no `(app)/loading.tsx`, um no
  `PaymentBrickCheckout`, um dentro do `Button`. Cada um com o seu CSS e keyframe.
- O sidebar troca o estado ativo de forma instantânea (sem transição), o drawer mobile
  abre/fecha com `display: none/block` (sem fade), e não há gesto de arrastar pra fechar.

Um usuário do piloto reportou a sensação de "travado" ao navegar no celular.

## Objetivo

1. **Feedback de navegação global** que aparece em **toda** troca de rota, sem tocar em
   cada página ou cada `<Link>`.
2. **Biblioteca `<Loader>`** única, nas cores do Domus, adaptada de um set de componentes
   Tailwind para CSS Modules. Substitui os spinners ad-hoc.
3. **Polimento de animação e usabilidade do sidebar.**

Escopo é UX/feedback visual. Não muda nenhuma regra de negócio, nenhum endpoint, nenhum
dado.

## Não-objetivos

- Não migrar os 53 arquivos que importam `next/link` nem os 135 sites de `router.push`.
- Não trocar o spinner do `Button` (é self-contained, baixo valor, e trocar aumenta o
  raio de impacto sem ganho real).
- Não adicionar dependência nova (barra de progresso é ~60 linhas nossas; `@bprogress/next`
  traz árvore transitiva nova e só entrega a barra dela, não o overlay nem o modo "link").
- Não adicionar infra de teste de frontend (o projeto não tem Jest/Vitest/Playwright;
  validação segue manual + `tsc`/`eslint`/`next build`).

---

## Peça A — Biblioteca `<Loader>`

### Origem

Um set de 12 loaders em React + Tailwind + shadcn foi fornecido como referência. O projeto
**não usa Tailwind nem shadcn** — usa CSS Modules + tokens de design (`src/styles/tokens.css`).
Os componentes são **reescritos** em CSS Modules preservando a mesma API pública e as mesmas
animações. Nenhuma dependência nova.

### Arquivos novos

- `frontend/src/components/common/Loader/Loader.tsx`
- `frontend/src/components/common/Loader/Loader.module.css`
- `frontend/src/app/demo/loaders/page.tsx`
- `frontend/src/app/demo/loaders/page.module.css`

### API

```ts
export interface LoaderProps {
  variant?:
    | 'circular' | 'classic' | 'pulse' | 'pulse-dot' | 'dots' | 'typing'
    | 'wave' | 'bars' | 'terminal' | 'text-blink' | 'text-shimmer' | 'loading-dots'
  size?: 'sm' | 'md' | 'lg'   // 16 / 20 / 24 px
  text?: string               // usado só pelas variantes de texto; default "Carregando"
  className?: string
}

export function Loader(props: LoaderProps): JSX.Element
// + exports nomeados por variante: CircularLoader, ClassicLoader, DotsLoader, etc.
```

- `variant` inválido / ausente cai em `circular`.
- Todos os componentes de variante aceitam `{ className?, size? }` (e `text?` nas de texto),
  igual ao set de referência.
- Elemento acessível: cada loader tem `<span className="sr-only">Carregando</span>` (ou o
  próprio texto, nas variantes de texto). O wrapper leva `role="status"` `aria-live="polite"`
  quando usado como indicador de página; inline nos botões não precisa.

### Mapa de cor (Tailwind de referência → token Domus)

| Referência | Token Domus |
|---|---|
| `bg-primary`, `border-primary`, `text-primary` | `var(--color-primary)` (#2563EB) |
| `--muted-foreground` | `var(--color-text-muted)` (#64748B) |
| `--foreground` | `var(--color-text-primary)` (#0F172A) |
| `border-t-transparent` etc. | `transparent` |
| gradiente do `text-shimmer` | `linear-gradient(to right, var(--color-text-muted) 40%, var(--color-text-primary) 60%, var(--color-text-muted) 80%)` |

Onde fizer sentido, o loader usa `currentColor` como default, pra o componente pai poder
controlar a cor via `color`.

### Keyframes

Todos os `@keyframes` do set de referência (`spin`, `spinner-fade`, `thin-pulse`,
`pulse-dot`, `bounce-dots`, `typing`, `wave`, `wave-bars`, `blink`, `text-blink`,
`shimmer`, `loading-dots`) vão dentro do `Loader.module.css`. CSS Modules escopa os nomes
de keyframe junto com as classes, então não há colisão com o `spin` que já existe no
`(app)/loading.module.css` ou no `Button.module.css`.

### `prefers-reduced-motion`

Bloco `@media (prefers-reduced-motion: reduce)` no módulo: variantes de rotação/translação
desaceleram para ~2s; variantes de texto (`text-blink`, `shimmer`) param a animação e
ficam estáticas no estado mais legível.

### Rota de demonstração — `demo/loaders`

- **Uma rota só**, cobre a peça A **e** a peça B.
- Guard: `if (process.env.NODE_ENV === 'production') notFound()` — não vaza em produção.
- Não fica dentro de `(app)` (não precisa de auth nem sidebar).
- Conteúdo:
  1. **Galeria de loaders:** grid com as 12 variantes girando, rótulo embaixo de cada uma,
     toggle de tamanho (sm / md / lg) que afeta todas. Variantes de texto mostram texto de
     exemplo.
  2. **Simulador de indicador de navegação:** três botões — "simular barra", "simular
     overlay", "simular barra + link". Cada um dispara o respectivo indicador por ~2s ali
     na própria página (sem navegar de verdade), pra dar pra ver o visual e o
     comportamento antes de escolher o modo real.

### Trocas imediatas (nesta peça)

- `frontend/src/app/(app)/loading.tsx` → renderiza `<Loader variant="circular" size="lg" />`
  dentro do wrapper centralizado que já existe. `loading.module.css` perde o `.spinner` e o
  `@keyframes spin` (fica só o `.wrapper`).
- `frontend/src/components/module/pagamento/PaymentBrickCheckout.tsx` → o `<span className={styles.spinner} />`
  vira `<Loader variant="circular" size="lg" />`. `PaymentBrickCheckout.module.css` perde
  `.spinner` e `@keyframes girar` (mantém `.brickArea`, `.carregando`, `.processando`).

### Limpeza posterior

Depois que o autor escolher no demo quais variantes ficam, um commit separado remove as
não escolhidas do `Loader.tsx` / `Loader.module.css` e ajusta a union de tipos. A rota
`demo/loaders` pode ser mantida (dev-only) ou removida — decisão do autor nesse momento.

---

## Peça B — `<NavProgress>` (indicador de navegação global)

### Ideia

Um provider client montado **uma vez** no `(app)/layout.tsx`. No mount ele **embrulha
`history.pushState` e `history.replaceState`**: toda navegação client-side do Next
(clique em `<Link>` ou `router.push`/`replace`) passa por um desses métodos. O wrapper
liga o estado "navegando". Um `useEffect` observando `[pathname, searchParams]` desliga
quando a rota nova terminou de renderizar.

Assim **nenhum arquivo de página é tocado** e navegação por voltar/avançar do navegador
(`popstate`) também é coberta.

### Estado — `frontend/src/store/uiStore.ts`

Adiciona ao `UiState` existente:

```ts
navsPendentes: number          // contador — clique rápido em sequência não desliga cedo
navegando: boolean             // derivado: navsPendentes > 0
iniciarNav: () => void         // navsPendentes++
finalizarNav: () => void       // navsPendentes = max(0, navsPendentes - 1)
resetarNav: () => void         // navsPendentes = 0 (timeout de segurança)
```

### Config — `frontend/src/config/navIndicator.ts` (novo)

```ts
// Modo do indicador de navegação. Trocar aqui, subir, testar. Sem reescrever nada.
export const MODO_INDICADOR_NAV: 'barra' | 'barra-e-link' = 'barra-e-link'
```

> Revisão: o modo `overlay` saiu daqui. Navegação de rota nunca bloqueia — quem clica pode
> ir pra outro canto enquanto a página nova monta. Overlay é só pra operação bloqueante
> (Peça B2).

Uma constante literal (não env) — é decisão de produto, fica versionada e o `tsc` cobre os
três valores.

### Componente — `frontend/src/components/layout/NavProgress/`

Arquivos: `NavProgress.tsx`, `NavProgress.module.css`, `BarraProgresso.tsx` (mesmo módulo
CSS). O overlay não vive mais aqui — virou `<OverlayCarregando>` genérico na Peça B2.

`NavProgress.tsx` (client):

- **`useEffect` no mount** (deps `[]`):
  - guarda `const pushOriginal = history.pushState` / `replaceOriginal`.
  - substitui por versão que chama o original e, **se o novo path+search for diferente do
    atual**, chama `iniciarNav()`. Mudança só de hash (`#`) não conta.
  - adiciona listener de `popstate` → `iniciarNav()`.
  - cleanup: restaura `pushOriginal`/`replaceOriginal`, remove o listener.
- **`useEffect` em `[pathname, searchParams]`**: rota nova renderizou → `finalizarNav()`.
  Ignora o primeiro run (mount inicial não é navegação).
- **Timeout de segurança:** ao `iniciarNav`, arma `setTimeout(resetarNav, 10000)`. Se a
  navegação terminar antes, limpa o timeout. Garante que a barra nunca fica eterna.
- **Delay + mínimo visível:** o indicador só aparece se `navegando` continuar `true` por
  **150ms** (nav instantânea não pisca); uma vez visível, fica no mínimo **400ms** (não
  treme em nav rápida). Lógica encapsulada num hook interno `useIndicadorVisivel(navegando)`
  que retorna um boolean já com esse debounce/hold.
- **Render:** sempre `<BarraProgresso ativo={visivel} />`. Nos dois modos a barra é igual;
  `'barra-e-link'` acrescenta o spinner no ícone do sidebar (responsabilidade do Sidebar,
  ver abaixo). Navegação nunca renderiza overlay.

`BarraProgresso.tsx`:

- `position: fixed; top: 0; left: 0; right: 0; height: 3px; z-index: 100` (acima da TopBar).
- Cor `var(--color-primary)` com um brilho (`box-shadow`) na ponta direita.
- `ativo` true: largura anima de 0 até ~85% com easing desacelerando (simula progresso).
- `ativo` vira false: largura → 100% rápido, depois `opacity` → 0 e reseta.
- Implementado com um `<div>` interno cuja `width`/`opacity` são controladas por classe +
  um pequeno `useState` de fase (`carregando` | `finalizando` | `oculto`).
- `prefers-reduced-motion`: sem trickle — aparece em ~70%, salta pra 100% no fim.

### Modo `'barra-e-link'` — Sidebar

Só neste modo, e só no `Sidebar.tsx` (arquivo único):

- Um subcomponente `<IconePendente icon={Icon} />` renderizado **como filho do `<Link>`**
  (requisito do `useLinkStatus` do Next). Quando `useLinkStatus().pending` é `true`, mostra
  `<Loader variant="circular" size="sm" />` no lugar do `<Icon />`.
- Aplica-se aos itens de nav e aos botões de submenu que são `<Link>`. Os botões de
  abrir/fechar submenu (não são navegação) ficam de fora.
- Fora do modo `'barra-e-link'`, `<IconePendente>` sempre renderiza só o ícone (custo
  zero).

### Arquivos tocados na peça B

- `frontend/src/store/uiStore.ts` — campos novos.
- `frontend/src/app/(app)/layout.tsx` — uma linha: `<NavProgress />` dentro do `AuthGuard`.
- `frontend/src/components/layout/Sidebar.tsx` — só o `<IconePendente>` (modo link).
- Novos: `config/navIndicator.ts`, `components/layout/NavProgress/*`.

---

## Peça B2 — `<OverlayCarregando>` (operação bloqueante)

Para ações em que clicar em outro lugar no meio quebra a operação: pagar, excluir,
arquivar, submeter cadastro. Diferente da navegação, **aqui o overlay faz sentido** —
segura a tela enquanto a ação resolve.

### Componente — `frontend/src/components/common/OverlayCarregando/`

Arquivos: `OverlayCarregando.tsx`, `OverlayCarregando.module.css`. É a evolução do que
seria o `OverlayNav`: mesmo visual (véu + spinner), mas genérico e com texto opcional.

```ts
interface OverlayCarregandoProps {
  ativo: boolean
  texto?: string        // ex.: "Processando pagamento…" — opcional
  /** 'fixed' cobre a viewport toda (default); 'absolute' cobre só o container-pai
   *  posicionado (ex.: dentro de um modal). */
  cobertura?: 'fixed' | 'absolute'
}
```

- `position: fixed` (ou `absolute`) `inset: 0; z-index` alto.
- Véu `var(--color-bg-overlay)` + `backdrop-filter: blur(2px)`.
- `<Loader variant="circular" size="lg" />` centralizado (branco, `filter` pra contraste)
  + `texto` embaixo, se houver.
- `pointer-events` no overlay bloqueia clique.
- Fade in/out 150ms; `role="status"` `aria-live="polite"`.
- `prefers-reduced-motion`: sem fade.

### Onde aplicar agora

1. **Checkout de pagamento (`PaymentBrickCheckout`):** só na ação **reiniciar** (`reiniciando`
   — "QR não funcionou / pagar de outro jeito", que chama o backend e hoje não tem feedback
   forte). **NÃO** no `enviando` do botão pagar — essa parte já tem a animação nativa do
   Payment Brick do Mercado Pago e fica como está.
2. **Modais de confirmação crítica:** `ModalConfirmacaoCritica` e `ModalExcluirIgreja` —
   `<OverlayCarregando cobertura="absolute" ativo={acaoEmAndamento} />` dentro do modal
   enquanto a ação roda.
3. **Submit de cadastro:** formulários de pessoa, evento e movimentação financeira —
   `<OverlayCarregando ativo={isSubmitting} texto="Salvando…" />` no submit.

### Arquivos tocados na peça B2

- Novos: `components/common/OverlayCarregando/*`.
- `PaymentBrickCheckout.tsx` — overlay no `reiniciando`.
- `ModalConfirmacaoCritica.tsx`, `ModalExcluirIgreja.tsx` — overlay na ação.
- Forms de pessoa/evento/movimentação — overlay no submit (identificar os arquivos exatos
  no plano).

---

## Peça C — Polimento do sidebar

Todos os quatro itens levantados pelo autor. Arquivos: `Sidebar.tsx`,
`Sidebar.module.css`, + `frontend/src/hooks/useArrastarParaFechar.ts` (novo). Nada fora do
sidebar.

### c1 — Indicador de item ativo desliza

- Um `<span className={styles.indicadorAtivo}>` posicionado (`position: absolute`) dentro
  de `.nav`.
- `useLayoutEffect` mede `offsetTop` e `offsetHeight` do link ativo (mapa de refs por
  `href`, ou `querySelector` do link com a classe ativa dentro do `<nav>`).
- Move o span com `transform: translateY(<offsetTop>px)` + `height: <offsetHeight>px`,
  `transition: transform .25s cubic-bezier(.16,1,.3,1), height .25s`.
- Troca de rota → a pílula desliza até o novo item, em vez de o fundo branco pular.
- O `.linkActive` perde o `background-color` branco (vira transparente) — o fundo agora é a
  pílula deslizante. Mantém `color` e `box-shadow`? A pílula leva o `background` + `box-shadow`;
  o texto ativo mantém `color: #1d4ed8`.
- Estado inicial (mount): posiciona sem transição (senão desliza do topo ao abrir a página).
- `prefers-reduced-motion`: sem `transition` — reposiciona instantâneo.
- Se nenhum item corresponde à rota (ex.: rota filha não listada), a pílula some
  (`opacity: 0`).

### c2 — Feedback de toque

Só CSS, sem lógica nova:

- `.link:active` — fundo mais evidente + `transform: scale(.98)` (já existe, refinar a
  curva).
- Ícone com micro-nudge: `.link:active svg { transform: scale(.92) }`.
- `transition` de hover mais suave (`background-color .18s ease`).
- `-webkit-tap-highlight-color: transparent` (já existe).
- Sem ripple (over-engineering).

### c3 — Drawer mobile

- **Backdrop:** hoje `.overlay` alterna `display: none` / `block`. Troca por estado
  permanente com `opacity: 0; visibility: hidden; transition: opacity .28s, visibility .28s`
  e `.overlayVisivel` → `opacity: 1; visibility: visible`. Fade de verdade, sincronizado
  com o deslize do `<aside>`.
- **Curva do deslize:** mantém `cubic-bezier(0.16, 1, 0.3, 1)` que já está lá.
- **Arrastar pra fechar** — `hooks/useArrastarParaFechar.ts`:
  - Assinatura: `useArrastarParaFechar({ aberta, aoFechar })` → retorna
    `{ handlers, estiloArraste }` onde `handlers` são `onTouchStart/Move/End` pro `<aside>`
    e `estiloArraste` é um `style` inline (`transform` + `transition: none` durante o
    arraste).
  - `touchstart`: guarda X inicial (só ativa se `aberta`).
  - `touchmove`: `delta = clientX - inicialX`; se `delta < 0` (arrastando pra esquerda,
    fechando), aplica `transform: translateX(delta)`. Ignora `delta > 0`.
  - `touchend`: se `|delta| > 40% da largura do aside` **ou** velocidade de flick alta →
    `aoFechar()`; senão volta a `translateX(0)` com transição.
  - Só toque. Desktop (`@media min-width: 768px`) nunca monta os handlers (o sidebar é
    fixo lá).
  - `prefers-reduced-motion`: o arraste em si continua (é resposta direta ao dedo), mas o
    "voltar" ao soltar é instantâneo.
- **Trava de scroll do body:** não existe hoje. Entra nesta peça — quando o drawer abre no
  mobile, `overflow: hidden` no `body` (ou `documentElement`) enquanto `navAberta`, revertido
  ao fechar. Efeito num `useEffect` dentro do `Sidebar` (ou do layout), com cleanup.

### c4 — Acordeão dos submenus

Mantém o mecanismo (grid `grid-template-rows: 0fr → 1fr`). Polimento:

- Easing do chevron: `transition: transform .2s cubic-bezier(.16,1,.3,1)`.
- Stagger dos itens do submenu: cada `.subLink` ganha `transition-delay` crescente
  (`nth-child`) quando `.submenuAberto` — aparecem em cascata rápida em vez de tudo junto.
  `opacity` + `translateY(-4px)` → `0` no estado aberto.
- Curva do `grid-template-rows` um tico mais elástica.
- `prefers-reduced-motion`: sem stagger, sem translate — só o grid abre/fecha direto.

---

## Peça D — animação em toda troca de conteúdo

> Adicionada durante a execução. Objetivo do autor: "nada seco, animação em toda parte".
> Guardrail mantido: **sem dependência nova** (nada de framer-motion).

Descoberta que molda o desenho: **não existe componente `<Tabs>`** — as abas do app
(configurações, financeiro, células, usuários, locais…) são **rotas** com um `layout.tsx`
que renderiza os links + `{children}`. Trocar de aba É navegar. Então uma transição de
conteúdo de rota cobre rota **e** abas de uma vez.

### D1 — `<TransicaoRota>` + View Transitions API

> Revisado durante a execução: o autor achou a entrada-só "seca" em aba com cache (troca
> instantânea, sem "saída"). Entrou a **View Transitions API** — sem lib, sem config
> `experimental` do Next, só a API nativa do browser chamada à mão.

Duas camadas, no mesmo `<TransicaoRota>` (client, envolve `{children}` no `(app)/layout.tsx`):

1. **`view-transition-name: conteudoApp`** (sempre) — o `<NavProgress>` chama
   `document.startViewTransition()` dentro do patch de `history.pushState`/`replaceState`/
   `popstate` que já existe. O browser tira snapshot do antes, deixa a navegação acontecer,
   e ao a rota nova renderizar (o `useEffect [pathname]` resolve a Promise da transição)
   faz o crossfade — **inclusive em troca instantânea de aba em cache**. Só o `<main>`
   anima (`::view-transition-*(conteudoApp)` em `globals.css`): sai `translateY(-10px)` +
   fade 0.26s, entra `translateY(16px)` + fade 0.4s. `root` fica com `animation: none`
   (sidebar/topbar parados). Promise com timeout de 500ms — navegação nunca trava.
2. **Keyframe de entrada** (`key={pathname}` remonta) — fallback pra quem não tem a API
   (Firefox). `TransicaoRota` detecta `'startViewTransition' in document` e só aplica a
   classe `.rota` quando **não** tem (senão as duas animações somam).

- `prefers-reduced-motion`: `::view-transition-*` com `animation: none !important`,
  `view-transition-name: none`, keyframe `none`.

> **Ajuste 2026-08-28 (2ª rodada de teste):** a VT estava larga demais. Agora:
> - **Só troca de seção** dispara VT — `deveAnimarVT` exige pathname irmão/não relacionado.
>   Mudança só de `search` (modal, filtro) e drill parent↔filho (drawer routeado) **não**
>   animam: o deslize próprio do modal/drawer cuida, e o crossfade ali piscava
>   sidebar/header. `popstate` só liga barra/VT se o pathname realmente mudou (voltar que
>   só fecha modal não anima).
> - VT **curta** (saída 0.14s, entrada 0.22s): durante a VT o browser mostra snapshot
>   estático, então spinner na tela congela — curto = piscar imperceptível.
> - `uiStore.navegando` virou **booleano** (era contador): `iniciar`/`finalizar` não são
>   1:1 (Next chama pushState+replaceState; navegação rápida pula rotas). O efeito
>   `[pathname]` cancela `iniciar` pendente e seta `false` — mata a barra presa.

### D2 — `<Transicao>` (primitiva pra blocos fora de rota)

- `frontend/src/components/common/Transicao/Transicao.tsx` + reusa o mesmo `.module.css`.
- `<Transicao modo="fade" | "subir" | "escala">{children}</Transicao>` — anima na montagem
  via `@starting-style` + `transition` (não keyframe, pra não re-disparar a cada render).
- Uso: resultado de filtro que aparece, seção condicional, painel que expande, preview de
  builder — qualquer bloco que hoje aparece do nada.
- `prefers-reduced-motion`: sem transição.

### D3 — `<ItemAnimado>` (entrada/saída de item de lista)

- `frontend/src/components/common/Transicao/ItemAnimado.tsx` + `.module.css`.
- Entrada: `@starting-style` (`opacity 0`, `translateX(-6px)`) → estado normal.
- Saída: o pai marca `saindo` (prop) antes de desmontar; o item roda a transição reversa +
  colapsa a altura (`max-height`/`opacity`), e só então o pai remove do array. Um hook
  auxiliar `useListaComSaida(itens, chave)` encapsula esse "segurar 1 ciclo antes de
  remover" — devolve a lista renderizável com os que estão saindo ainda dentro, marcados.
- **Reordenação (FLIP) fica de fora** — é o caso que de verdade pediria framer-motion.
  Reabrir se o autor quiser depois.
- Aplicar nas listas onde add/remove é comum e visível: acompanhantes na inscrição,
  contribuintes na movimentação, campos personalizados no builder de evento. Não sair
  aplicando em toda `.map` do app — YAGNI, aplica onde o add/remove é interação real.
- `prefers-reduced-motion`: entra/sai sem deslocamento, só `opacity` instantâneo.

### Arquivos tocados na peça D

- Novos: `components/common/Transicao/{TransicaoRota,Transicao,ItemAnimado}.tsx` +
  `Transicao.module.css` + `hooks/useListaComSaida.ts`.
- `frontend/src/app/(app)/layout.tsx` — envolve `{children}` no `<TransicaoRota>`.
- 2–3 formulários/builders pra `<ItemAnimado>` (identificados no plano).
- `demo/loaders` (renomear mentalmente pra "demo") ganha uma seção mostrando D1/D2/D3.

---

## Fluxo ponta a ponta (peça B)

1. Usuário clica num `<Link>` (ex.: "Eventos" no sidebar) ou um `router.push` roda.
2. Next chama `history.pushState` → o wrapper detecta path diferente → `iniciarNav()` →
   `navsPendentes = 1`, arma timeout de 10s.
3. `useIndicadorVisivel` espera 150ms; se ainda navegando, `visivel = true`.
4. `NavProgress` renderiza `<BarraProgresso ativo />` (ou overlay) conforme a constante.
5. RSC payload chega, React renderiza `/eventos`, `pathname` muda.
6. `useEffect [pathname, searchParams]` → `finalizarNav()` → `navsPendentes = 0` →
   `navegando = false`, limpa o timeout.
7. `useIndicadorVisivel` respeita o mínimo de 400ms visível, depois `visivel = false`.
8. `BarraProgresso` completa pra 100%, faz fade, reseta.

## Tratamento de erro / casos de borda

- **Navegação que falha (erro no servidor):** o `error.tsx`/boundary do Next renderiza →
  `pathname` pode não mudar → o timeout de 10s (`resetarNav`) garante que a barra some.
- **Clique repetido no mesmo link:** path igual → `pushState` wrapper não chama
  `iniciarNav`. Sem efeito.
- **Duas navegações encavaladas:** contador (`navsPendentes`) — só zera quando todas
  terminam.
- **`demo/loaders` em produção:** `notFound()`.
- **`useLinkStatus` fora de `<Link>`:** só é usado dentro do `<IconePendente>`, que é
  sempre filho de `<Link>`. Sem risco.
- **Sidebar sem item correspondente à rota:** pílula do c1 fica `opacity: 0`.

## Testes / validação

O projeto não tem infra de teste de frontend (sem Jest/Vitest/Playwright — dívida técnica
conhecida, documentada no CLAUDE.md). Validação de cada peça:

- **Automático:** `npx tsc --noEmit`, `npx eslint <arquivos>`, `next build` — todos limpos,
  zero erro novo (comparar contra `git stash` se o baseline já tiver lint pendente).
- **Manual, peça A:** abrir `demo/loaders`, ver as 12 variantes nos 3 tamanhos; conferir
  `(app)/loading` e o checkout do Mercado Pago ainda mostram spinner.
- **Manual, peça B:** navegar entre telas (sidebar, cards, botões que fazem `router.push`),
  ver a barra; voltar/avançar do navegador; testar `'barra'` e `'barra-e-link'` trocando a
  constante; simular no `demo/loaders`.
- **Manual, peça B2:** disparar reiniciar no checkout, excluir/arquivar num modal crítico,
  submeter um cadastro — o overlay aparece e some; clicar por baixo não passa.
- **Manual, peça C:** viewport mobile (Chrome DevTools, 390×844) — abrir/fechar drawer,
  arrastar pra fechar, abrir submenus, trocar de rota e ver a pílula deslizar; repetir no
  desktop; ativar "reduce motion" no SO e reconferir.

## Ordem de implementação (pedaços testáveis)

Entregar um, esperar o autor testar, commit, próximo.

1. **Peça A** — `Loader` + `Loader.module.css` + rota `demo/loaders` + trocas em
   `(app)/loading.tsx` e `PaymentBrickCheckout`.
2. **Peça B** — `uiStore` + `config/navIndicator.ts` + `NavProgress/*` + linha no
   `(app)/layout.tsx` + `<IconePendente>` no Sidebar. Default `'barra-e-link'`.
3. **Peça B2** — `<OverlayCarregando>` + aplicar no reiniciar do checkout, nos modais
   críticos e no submit dos cadastros.
4. **Peça C** — sidebar c1 → c2 → c3 → c4.
5. **Peça D** — D1 (`<TransicaoRota>` no layout) → D2 (`<Transicao>`) → D3
   (`<ItemAnimado>` + hook + aplicar em 2–3 listas).

Depois: commit de limpeza removendo as variantes de loader não escolhidas.

## Arquivos — resumo

**Novos:**
- `frontend/src/components/common/Loader/Loader.tsx`
- `frontend/src/components/common/Loader/Loader.module.css`
- `frontend/src/app/demo/loaders/page.tsx`
- `frontend/src/app/demo/loaders/page.module.css`
- `frontend/src/config/navIndicator.ts`
- `frontend/src/components/layout/NavProgress/NavProgress.tsx`
- `frontend/src/components/layout/NavProgress/NavProgress.module.css`
- `frontend/src/components/layout/NavProgress/BarraProgresso.tsx`
- `frontend/src/components/common/OverlayCarregando/OverlayCarregando.tsx` + `.module.css`
- `frontend/src/hooks/useArrastarParaFechar.ts`

**Modificados:**
- `frontend/src/app/(app)/loading.tsx` + `loading.module.css`
- `frontend/src/components/module/pagamento/PaymentBrickCheckout.tsx` + `.module.css`
- `frontend/src/store/uiStore.ts`
- `frontend/src/app/(app)/layout.tsx`
- `frontend/src/components/layout/Sidebar.tsx` + `Sidebar.module.css`
- `frontend/src/components/common/ModalConfirmacaoCritica/ModalConfirmacaoCritica.tsx`
- `frontend/src/components/module/configuracoes/ModalExcluirIgreja/ModalExcluirIgreja.tsx`
- Forms de cadastro de pessoa / evento / movimentação (submit)

**Sem dependência nova. Sem mudança de backend.**
