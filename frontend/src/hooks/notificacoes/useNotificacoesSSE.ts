'use client'

import { useEffect } from 'react'
import { useQueryClient } from '@tanstack/react-query'

// Abre uma conexão SSE (mesma origem, cookie httpOnly vai junto sem configuração
// extra) e invalida as queries de notificação assim que o backend avisa que algo
// mudou — o polling de useContagemNaoLidas continua como rede de segurança (conexão
// caiu, aba dormiu), mas deixa de ser o caminho principal.
export function useNotificacoesSSE() {
  const queryClient = useQueryClient()

  useEffect(() => {
    const eventSource = new EventSource('/api/notificacoes/stream')

    function avisar() {
      queryClient.invalidateQueries({ queryKey: ['notificacoes'] })
    }

    eventSource.addEventListener('nova-notificacao', avisar)

    return () => {
      eventSource.removeEventListener('nova-notificacao', avisar)
      eventSource.close()
    }
  }, [queryClient])
}
