import axios from 'axios'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { notificar } from '@/components/common/Notificacao/notificar'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { inscricoesService } from '@/services/inscricao.service'
import type { ApiError } from '@/types/api.types'

/** Cancela uma inscrição — a própria (membro) ou de outro (ADMIN/LÍDER), regra fica no backend. */
export function useCancelarInscricao() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (inscricaoId: string) => inscricoesService.cancelar(inscricaoId),
    onSuccess: () => {
      invalidarCache(queryClient, 'inscricao')
      notificar.sucesso('Inscrição cancelada.')
    },
    onError: (error: unknown) => {
      const mensagem = axios.isAxiosError<ApiError>(error)
        ? error.response?.data?.message
        : undefined
      notificar.erro('Não foi possível cancelar a inscrição', mensagem ?? 'Tente novamente.')
    },
  })
}
