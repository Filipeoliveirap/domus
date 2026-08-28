import { useCallback, useRef, useState } from 'react'

/**
 * Anima a SAÍDA de um modal/drawer controlado pelo pai (`{aberto && <Modal/>}`): em vez de
 * chamar `onClose` na hora (desmonte seco), marca `saindo`, deixa a animação de saída
 * rodar por `duracao` ms e só então chama `onClose` de verdade.
 *
 * Uso: `const { saindo, fechar } = useFecharAnimado(onClose)` — chame `fechar` no X, no
 * clique fora e no Escape; adicione a classe de saída quando `saindo` for true.
 */
export function useFecharAnimado(onClose: () => void, duracao = 180) {
  const [saindo, setSaindo] = useState(false)
  const jaFechando = useRef(false)

  const fechar = useCallback(() => {
    if (jaFechando.current) return
    jaFechando.current = true
    setSaindo(true)
    setTimeout(onClose, duracao)
  }, [onClose, duracao])

  return { saindo, fechar }
}
