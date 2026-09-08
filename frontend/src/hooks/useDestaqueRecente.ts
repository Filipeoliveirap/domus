'use client'

import { useState } from 'react'
import { useRouter, useSearchParams, usePathname } from 'next/navigation'

/**
 * Depois de cadastrar/editar alguém, a tela volta pra uma lista em ordem alfabética — a
 * pessoa recém-salva pode cair lá no meio e o único sinal é o toast. Este hook lê um
 * `?destaque=<id>` da URL (o form de cadastro/edição navega com ele), fixa esse item no
 * topo da lista **só nesta visita** e limpa o parâmetro da URL. Entrar na aba de novo
 * (sem o parâmetro) volta a ordem normal.
 *
 * Uso: `const { destaqueId, ordenar } = useDestaqueRecente()` e
 * `ordenar(itens, i => i.id)` na renderização; a linha com `id === destaqueId` ganha o
 * realce de entrada (classe própria da tela).
 */
export function useDestaqueRecente(param = 'destaque') {
  const searchParams = useSearchParams()
  const router = useRouter()
  const pathname = usePathname()

  // Congela no primeiro render — o `router.replace` abaixo tira o parâmetro da URL, mas a
  // linha continua fixada pelo resto da visita.
  const [destaqueId] = useState(() => searchParams.get(param))
  const [limpou, setLimpou] = useState(false)

  if (destaqueId && !limpou) {
    setLimpou(true)
    const p = new URLSearchParams(searchParams.toString())
    p.delete(param)
    const qs = p.toString()
    // fora do render (o lint proíbe navegar dentro dele; mesmo motivo de useBuscaUrl)
    queueMicrotask(() => router.replace(qs ? `${pathname}?${qs}` : pathname, { scroll: false }))
  }

  function ordenar<T>(lista: T[], getId: (item: T) => string): T[] {
    if (!destaqueId) return lista
    const alvo = lista.find((i) => getId(i) === destaqueId)
    if (!alvo) return lista
    return [alvo, ...lista.filter((i) => getId(i) !== destaqueId)]
  }

  return { destaqueId, ordenar }
}
