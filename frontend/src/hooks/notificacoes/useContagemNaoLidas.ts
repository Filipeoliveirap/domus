import { useQuery } from '@tanstack/react-query'
import { notificacaoCentralService } from '@/services/notificacaoCentral.service'

// Polling: sem WebSocket no projeto, 30s é suficiente pro volume de uma igreja.
export function useContagemNaoLidas() {
  return useQuery({
    queryKey: ['notificacoes', 'contagem-nao-lidas'],
    queryFn: () => notificacaoCentralService.contagemNaoLidas(),
    refetchInterval: 30 * 1000,
  })
}
