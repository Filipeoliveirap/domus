import axios from 'axios'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { notificar } from '@/components/common/Notificacao/notificar'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { inscricoesService } from '@/services/inscricao.service'
import type { ApiError } from '@/types/api.types'

// `silencioso` evita toast no modo "Eu vou": desmarcar presença não é cancelamento formal.
export function useCancelarInscricao(silencioso = false) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (inscricaoId: string) => inscricoesService.cancelar(inscricaoId),
    onSuccess: () => {
      invalidarCache(queryClient, 'inscricao')
      if (!silencioso) notificar.sucesso('Inscrição cancelada.')
    },
    onError: (error: unknown) => {
      const mensagem = axios.isAxiosError<ApiError>(error)
        ? error.response?.data?.message
        : undefined
      notificar.erro('Não foi possível cancelar a inscrição', mensagem ?? 'Tente novamente.')
    },
  })
}
