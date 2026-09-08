'use client'

import { useCallback, useRef, useState } from 'react'

/**
 * Depois de salvar um formulário, o `router.back()` cortava a tela seca. Aqui a página
 * marca `saindo` (a classe global `saida-formulario` faz um fade + subida curta) e só
 * navega ~180ms depois — tempo do toast de sucesso registrar e da saída rodar. Em
 * navegadores com View Transitions o "antes" já sai desbotado, então não briga com o
 * crossfade da rota.
 */
export function useSaidaFormulario(duracao = 180) {
  const [saindo, setSaindo] = useState(false)
  const jaSaindo = useRef(false)

  const sairAnimado = useCallback((navegar: () => void) => {
    if (jaSaindo.current) return
    jaSaindo.current = true
    setSaindo(true)
    window.setTimeout(navegar, duracao)
  }, [duracao])

  return { saindo, sairAnimado }
}
