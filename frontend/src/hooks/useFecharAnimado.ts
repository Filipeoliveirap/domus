import { useCallback, useRef, useState } from 'react'

/**
 * Anima a SAÍDA de um modal/drawer: em vez de chamar `onClose` na hora (desmonte seco),
 * marca `saindo`, deixa a animação de saída rodar por `duracao` ms e só então chama
 * `onClose` de verdade.
 *
 * Feito pro padrão `{aberto && <Modal/>}` — o `onClose` desmonta o componente, então o
 * estado interno (`saindo`) some junto e reabrir já começa limpo. NÃO reseta `saindo` por
 * conta própria: fazer isso enquanto o componente ainda está montado (ex.: `onClose` que
 * navega via router, que é assíncrono) tira a classe de saída no meio da animação e o
 * modal "pisca de volta" antes de sumir.
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
