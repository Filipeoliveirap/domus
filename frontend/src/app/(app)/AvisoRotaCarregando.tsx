'use client'

import { useEffect } from 'react'

/**
 * Renderizado dentro de `loading.tsx`. Ao montar, avisa o <NavProgress> que a rota nova
 * caiu num estado de loading — ele aborta a View Transition em andamento pra o spinner
 * real aparecer girando, em vez de ficar congelado no snapshot da transição.
 */
export function AvisoRotaCarregando() {
  useEffect(() => {
    window.dispatchEvent(new Event('domus:rota-carregando'))
  }, [])
  return null
}
