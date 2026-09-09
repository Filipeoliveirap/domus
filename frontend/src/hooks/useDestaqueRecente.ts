'use client'

import { useEffect, useRef, useState } from 'react'
import { useRouter, useSearchParams, usePathname } from 'next/navigation'

/**
 * Depois de cadastrar/editar alguém, a tela volta pra uma lista em ordem alfabética — a
 * pessoa recém-salva pode cair lá no meio e o único sinal é o toast. Este hook lê um
 * `?destaque=<id>` da URL (o form de cadastro/edição navega com ele), congela esse id pro
 * resto da visita e limpa o parâmetro da URL num effect (fora do render). Entrar na aba
 * de novo, sem o parâmetro, volta a ordem normal.
 *
 * Uso: `const { destaqueId, ehNovo, ordenar } = useDestaqueRecente()`. A tela busca o item
 * à parte e o fixa no topo (ou usa `ordenar(itens, i => i.id)` quando o item já está na
 * página), e dá o realce de entrada na linha com `id === destaqueId`.
 *
 * `ehNovo` (do `?novo=1`) marca "veio de um cadastro, não de uma edição" — numa lista
 * cronológica a linha nova já nasce no topo, então a tela pode só realçar sem fixar.
 */
export function useDestaqueRecente(param = 'destaque') {
  const searchParams = useSearchParams()
  const router = useRouter()
  const pathname = usePathname()

  // Captura o `?destaque` na PRIMEIRA vez que ele aparece (em rota estática o
  // useSearchParams pode vir vazio no 1º render e só popular depois) e congela — o effect
  // abaixo tira o parâmetro da URL, mas o id continua fixando a linha pelo resto da visita.
  const naUrl = searchParams.get(param)
  const novoNaUrl = searchParams.get('novo') === '1'
  const [destaqueId, setDestaqueId] = useState<string | null>(naUrl)
  // `ehNovo` distingue cadastro de edição: numa lista cronológica (ex.: movimentações) a
  // linha recém-criada já nasce no topo, então não precisa ser fixada — só realçada.
  const [ehNovo, setEhNovo] = useState(novoNaUrl)
  if (destaqueId === null && naUrl) {
    setDestaqueId(naUrl)
    setEhNovo(novoNaUrl)
  }
  const limpou = useRef(false)

  useEffect(() => {
    if (!destaqueId || limpou.current) return
    limpou.current = true
    const p = new URLSearchParams(window.location.search)
    p.delete(param)
    p.delete('novo')
    const qs = p.toString()
    router.replace(qs ? `${pathname}?${qs}` : pathname, { scroll: false })
  }, [destaqueId, param, pathname, router])

  function ordenar<T>(lista: T[], getId: (item: T) => string): T[] {
    if (!destaqueId) return lista
    const alvo = lista.find((i) => getId(i) === destaqueId)
    if (!alvo) return lista
    return [alvo, ...lista.filter((i) => getId(i) !== destaqueId)]
  }

  return { destaqueId, ehNovo, ordenar }
}
