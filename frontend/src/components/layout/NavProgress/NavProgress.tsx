'use client'

import { useEffect, useRef, useState } from 'react'
import { usePathname, useSearchParams } from 'next/navigation'
import { useUiStore } from '@/store/uiStore'
import { BarraProgresso } from './BarraProgresso'

const DELAY_MOSTRAR = 150
const MIN_VISIVEL = 400
const TIMEOUT_SEGURANCA = 10000

// A View Transitions API ainda não está nos types do lib.dom padrão.
type ViewTransition = {
  ready: Promise<unknown>
  finished: Promise<unknown>
  updateCallbackDone: Promise<unknown>
  skipTransition: () => void
}
type DocumentComVT = Document & {
  startViewTransition: (cb: () => void | Promise<void>) => ViewTransition
}

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

  const visivel = useIndicadorVisivel(navegando)
  const primeiroRender = useRef(true)
  const seguranca = useRef<ReturnType<typeof setTimeout> | null>(null)
  // true entre "navegação disparada" e "rota nova renderizou". Se o pathname mudar antes
  // do microtask do iniciarNav rodar (rota em cache), o microtask vê false e não liga a
  // barra — evita a barra presa em navegação instantânea.
  const navPendente = useRef(false)
  // Pathname do render atual — o popstate compara com o novo `location.pathname` pra
  // saber se o "voltar" trocou de rota ou só fechou um modal (mudou só a query).
  const pathAtualRef = useRef(pathname)
  // Resolve a Promise da View Transition em andamento (quando a rota nova termina de
  // renderizar). O timeout de 500ms garante que a transição nunca trave a página.
  const vtResolver = useRef<(() => void) | null>(null)
  const vtEmAndamento = useRef(false)

  // Patch de history: toda navegação client-side do Next passa por pushState/replaceState.
  useEffect(() => {
    const pushOriginal = history.pushState.bind(history)
    const replaceOriginal = history.replaceState.bind(history)

    // A barra de progresso liga em qualquer troca de PATHNAME (inclusive abrir um drawer
    // de detalhe, que costuma buscar dados). Mudança só de search (modal, filtro,
    // paginação) não liga barra.
    const mudouPathname = (url?: string | URL | null) => {
      if (url == null) return false
      try {
        return new URL(url, window.location.href).pathname !== window.location.pathname
      } catch {
        return false
      }
    }

    // A View Transition (crossfade do conteúdo) roda SÓ em troca de tela (pathname).
    // Mudança de filtro/paginação (só a query) não anima — o autor achou o crossfade da
    // tela inteira "pulsado" nesse caso.
    const deveAnimarVT = (url?: string | URL | null) => mudouPathname(url)

    const armarSeguranca = () => {
      if (seguranca.current) clearTimeout(seguranca.current)
      seguranca.current = setTimeout(() => {
        navPendente.current = false
        finalizarNav()
      }, TIMEOUT_SEGURANCA)
    }

    // O Next chama history.pushState de dentro de um useInsertionEffect durante a
    // transição de rota; disparar um set() do zustand ali estoura
    // "useInsertionEffect must not schedule updates". queueMicrotask adia pro fim do tick.
    // Se o pathname já mudou até lá (rota em cache), navPendente já é false → não liga a
    // barra à toa.
    const marcarNavegando = () => {
      navPendente.current = true
      queueMicrotask(() => {
        if (!navPendente.current) return
        iniciarNav()
        armarSeguranca()
      })
    }

    const podeAnimar = () =>
      typeof document !== 'undefined' &&
      'startViewTransition' in document &&
      !window.matchMedia('(prefers-reduced-motion: reduce)').matches

    // Faz o browser tirar um snapshot do estado atual, roda `aplicar` (a navegação de
    // fato), e faz crossfade entre o antes e o depois — mas SÓ quando a troca é rápida
    // (rota em cache / filtro). Se demora >200ms ou a rota nova cai em loading.tsx, aborta:
    // durante a VT o DOM é um snapshot estático, então um spinner ali ficaria congelado.
    // A rota nova pode cair em loading.tsx e um spinner congelaria no snapshot estático da
    // VT — por isso aborta a transição se ela demorar (>200ms) ou se o loading.tsx montar.
    const comViewTransition = (aplicar: () => void) => {
      if (!podeAnimar() || vtEmAndamento.current) {
        aplicar()
        return
      }
      vtEmAndamento.current = true
      // O `view-transition-name` no conteúdo só existe enquanto [data-vt] está no <html>
      // (regra em globals.css) — fora daí seria um stacking context fixo que prende modais.
      const raiz = document.documentElement
      raiz.setAttribute('data-vt', '')
      void raiz.offsetWidth // força o recálculo antes do snapshot

      let resolvePromise: (() => void) | undefined
      let abortou = false
      const abortar = () => {
        if (abortou) return
        abortou = true
        try { vt.skipTransition() } catch { /* browser sem skipTransition */ }
        resolvePromise?.()
      }

      const vt = (document as DocumentComVT).startViewTransition(() => {
        aplicar()
        return new Promise<void>((resolve) => {
          resolvePromise = resolve
          vtResolver.current = () => {
            // rota nova ainda em loading.tsx → não vale crossfade (spinner congelaria)
            if (document.querySelector('[data-transicao-rota] [data-app-loading]')) abortar()
            else resolve()
          }
        })
      })

      // skipTransition() rejeita as 3 promises da transição com AbortError — todas
      // esperadas, todas engolidas (senão viram "Uncaught (in promise)").
      vt.ready.catch(() => {})
      vt.updateCallbackDone.catch(() => {})

      // nav lenta = vai ter spinner → aborta pra ele aparecer vivo
      const timerAbortar = setTimeout(abortar, 200)
      const timerSeguranca = setTimeout(() => resolvePromise?.(), 500)
      const aoCarregar = () => abortar()
      window.addEventListener('domus:rota-carregando', aoCarregar)

      // `skipTransition()` faz `vt.finished` rejeitar com AbortError — esperado, engole.
      vt.finished.catch(() => {}).finally(() => {
        clearTimeout(timerAbortar)
        clearTimeout(timerSeguranca)
        window.removeEventListener('domus:rota-carregando', aoCarregar)
        raiz.removeAttribute('data-vt')
        vtEmAndamento.current = false
        vtResolver.current = null
      })
    }

    history.pushState = function (data, unused, url) {
      if (mudouPathname(url)) marcarNavegando()
      if (deveAnimarVT(url)) {
        comViewTransition(() => pushOriginal(data, unused, url))
        return
      }
      return pushOriginal(data, unused, url)
    }
    history.replaceState = function (data, unused, url) {
      if (mudouPathname(url)) marcarNavegando()
      if (deveAnimarVT(url)) {
        comViewTransition(() => replaceOriginal(data, unused, url))
        return
      }
      return replaceOriginal(data, unused, url)
    }

    // Voltar/avançar: a URL já mudou quando o evento chega. Só anima se o pathname mudou
    // de verdade (um "voltar" que só fecha um modal muda só a query → não anima).
    const aoVoltar = () => {
      if (window.location.pathname === pathAtualRef.current) return
      marcarNavegando()
      comViewTransition(() => {})
    }
    window.addEventListener('popstate', aoVoltar)

    return () => {
      history.pushState = pushOriginal
      history.replaceState = replaceOriginal
      window.removeEventListener('popstate', aoVoltar)
      if (seguranca.current) clearTimeout(seguranca.current)
    }
  }, [iniciarNav, finalizarNav])

  // Rota nova renderizou → cancela iniciar pendente, fecha a navegação, libera a View Transition.
  useEffect(() => {
    if (primeiroRender.current) {
      primeiroRender.current = false
      return
    }
    navPendente.current = false
    pathAtualRef.current = pathname
    vtResolver.current?.()
    finalizarNav()
    if (seguranca.current) {
      clearTimeout(seguranca.current)
      seguranca.current = null
    }
  }, [pathname, searchParams, finalizarNav])

  // Navegação de rota nunca bloqueia — sempre a barra. O spinner no ícone do sidebar
  // (modo 'barra-e-link') é responsabilidade do próprio Sidebar.
  return <BarraProgresso ativo={visivel} />
}
