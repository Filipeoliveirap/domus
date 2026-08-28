'use client'

import { useEffect, useRef, useState } from 'react'
import { usePathname, useSearchParams } from 'next/navigation'
import { useUiStore } from '@/store/uiStore'
import { BarraProgresso } from './BarraProgresso'

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

  // Navegação de rota nunca bloqueia — sempre a barra. O spinner no ícone do sidebar
  // (modo 'barra-e-link') é responsabilidade do próprio Sidebar.
  return <BarraProgresso ativo={visivel} />
}
