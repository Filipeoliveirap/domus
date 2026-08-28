import { useRef, useState } from 'react'
import type { CSSProperties, TouchEvent } from 'react'

const LIMITE_FRACAO = 0.4 // arrastou 40% da largura → fecha
const FLICK_PX = 60 // ou um flick rápido de 60px
const LARGURA_ASIDE = 256 // .sidebar width: 16rem

interface Opts {
  aberta: boolean
  aoFechar: () => void
}

/**
 * Swipe-to-close pro drawer mobile: segue o dedo no translateX (só pra esquerda) e fecha
 * se passar do limite ou for um flick rápido. Desktop não deve montar isto (sidebar é fixo).
 */
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
    const dt = Date.now() - inicioT.current
    const passouLimite = Math.abs(delta) > LARGURA_ASIDE * LIMITE_FRACAO
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
