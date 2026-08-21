import { useMutation, useQueryClient } from '@tanstack/react-query'
import { notificacaoCentralService } from '@/services/notificacaoCentral.service'

export function useMarcarNotificacaoLida() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => notificacaoCentralService.marcarLida(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notificacoes'] })
    },
  })
}

export function useMarcarTodasNotificacoesLidas() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => notificacaoCentralService.marcarTodasLidas(),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notificacoes'] })
    },
  })
}
