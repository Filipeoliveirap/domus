'use client'

import { useEffect, useState } from 'react'
import { usePathname } from 'next/navigation'
import styles from './Transicao.module.css'

/**
 * Faz o conteúdo da rota (e das abas, que no app são rotas) trocar animado a cada
 * navegação. Duas camadas:
 *  - `view-transition-name` (sempre) → a View Transitions API, disparada no <NavProgress>,
 *    faz o crossfade entre a rota antiga e a nova, inclusive quando a troca é instantânea
 *    (rota em cache). Só o conteúdo anima; sidebar/topbar ficam parados.
 *  - keyframe de entrada (`key={pathname}` força remontar) → fallback pra browsers sem
 *    View Transitions (Firefox). Fica desligado quando a API existe, pra não somar as duas.
 */
export function TransicaoRota({ children }: { children: React.ReactNode }) {
  const pathname = usePathname()
  const [temViewTransition, setTemViewTransition] = useState(true)

  useEffect(() => {
    // microtask: fora do corpo do efeito (o lint proíbe setState síncrono ali). Começa
    // `true` no SSR/1º render (sem mismatch) e corrige pra `false` só em quem não tem a API.
    queueMicrotask(() => setTemViewTransition('startViewTransition' in document))
  }, [])

  return (
    <div
      key={pathname}
      data-transicao-rota=""
      className={temViewTransition ? undefined : styles.rota}
    >
      {children}
    </div>
  )
}
