'use client'

import { useLayoutEffect, useRef } from 'react'

/**
 * Anima a mudança de posição das linhas de uma lista quando a ordem muda (técnica FLIP:
 * mede a posição antiga, deixa o React repintar na ordem nova, aplica o deslocamento
 * inverso sem transição e solta com transição — a linha "desliza" pro lugar novo em vez
 * de piscar). Usado quando tornar/remover liderança reordena a lista.
 *
 * Uso:
 *   const listaRef = useReordenacaoAnimada(membros.map(m => m.id + m.papel).join(','))
 *   <ul ref={listaRef}> {membros.map(m => <li data-flip-id={m.id} key={m.id}>…</li>)} </ul>
 *
 * `chave` deve mudar sempre que a ordem renderizada mudar.
 */
export function useReordenacaoAnimada<T extends HTMLElement = HTMLElement>(
  chave: string,
  duracao = 420,
) {
  const ref = useRef<T | null>(null)
  const posicoesAnteriores = useRef<Map<string, number>>(new Map())

  useLayoutEffect(() => {
    const container = ref.current
    if (!container) return

    const reduzMovimento = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    const linhas = container.querySelectorAll<HTMLElement>('[data-flip-id]')
    const posicoesNovas = new Map<string, number>()

    linhas.forEach((linha) => {
      const id = linha.dataset.flipId
      if (!id) return
      const topoNovo = linha.getBoundingClientRect().top
      posicoesNovas.set(id, topoNovo)

      const topoAntigo = posicoesAnteriores.current.get(id)
      if (topoAntigo == null || reduzMovimento) return

      const delta = topoAntigo - topoNovo
      if (!delta) return

      // Inverte: coloca a linha visualmente onde ela estava, sem transição…
      linha.style.transition = 'none'
      linha.style.transform = `translateY(${delta}px)`

      // …e no próximo frame solta com transição, deslizando pro lugar novo.
      requestAnimationFrame(() => {
        linha.style.transition = `transform ${duracao}ms cubic-bezier(0.22, 1, 0.36, 1)`
        linha.style.transform = ''
      })

      // Limpa os estilos inline no fim pra não interferir no :active/hover da linha.
      linha.addEventListener(
        'transitionend',
        () => {
          linha.style.transition = ''
          linha.style.transform = ''
        },
        { once: true },
      )
    })

    posicoesAnteriores.current = posicoesNovas
  }, [chave, duracao])

  return ref
}
