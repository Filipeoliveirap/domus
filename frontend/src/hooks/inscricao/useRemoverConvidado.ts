import axios from 'axios'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { notificar } from '@/components/common/Notificacao/notificar'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { inscricoesService } from '@/services/inscricao.service'
import type { ApiError } from '@/types/api.types'

/** Remove um convidado (chama `/acompanhantes/{id}` no backend). */
export function useRemoverConvidado() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (acompanhanteId: string) => inscricoesService.removerAcompanhante(acompanhanteId),
    onSuccess: () => {
      invalidarCache(queryClient, 'inscricao')
      notificar.sucesso('Convidado removido.')
    },
    onError: (error: unknown) => {
      const mensagem = axios.isAxiosError<ApiError>(error)
        ? error.response?.data?.message
        : undefined
      notificar.erro('Não foi possível remover o convidado', mensagem ?? 'Tente novamente.')
    },
  })
}
