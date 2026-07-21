import axios from 'axios'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { notificar } from '@/components/common/Notificacao/notificar'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { inscricoesService } from '@/services/inscricao.service'
import type { ApiError } from '@/types/api.types'

/** Auto-inscrição do próprio usuário no evento. */
export function useInscrever(eventoId: string) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: () => inscricoesService.inscrever(eventoId),
    onSuccess: () => {
      invalidarCache(queryClient, 'inscricao')
      notificar.sucesso('Inscrição confirmada!')
    },
    onError: (error: unknown) => {
      const mensagem = axios.isAxiosError<ApiError>(error)
        ? error.response?.data?.message
        : undefined
      notificar.erro('Não foi possível se inscrever', mensagem ?? 'Tente novamente.')
    },
  })
}
