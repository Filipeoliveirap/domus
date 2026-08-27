import axios from 'axios'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { notificar } from '@/components/common/Notificacao/notificar'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { inscricoesService } from '@/services/inscricao.service'
import type { ApiError } from '@/types/api.types'

/** Remove um convidado (chama `DELETE /inscricoes/{id}` no backend — convidado é uma InscricaoEvento). */
export function useRemoverConvidado() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (inscricaoId: string) => inscricoesService.cancelar(inscricaoId),
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
