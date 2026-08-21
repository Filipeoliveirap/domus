import { useQuery } from '@tanstack/react-query'
import { notificacaoCentralService } from '@/services/notificacaoCentral.service'

// Rede de segurança: a entrega principal é via SSE (useNotificacoesSSE), esse
// polling só cobre o caso de a conexão SSE cair/aba ficar suspensa.
export function useContagemNaoLidas() {
  return useQuery({
    queryKey: ['notificacoes', 'contagem-nao-lidas'],
    queryFn: () => notificacaoCentralService.contagemNaoLidas(),
    refetchInterval: 60 * 1000,
  })
}
