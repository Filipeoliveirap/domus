import axios from 'axios'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { notificar } from '@/components/common/Notificacao/notificar'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { inscricoesService } from '@/services/inscricao.service'
import type { ApiError } from '@/types/api.types'

/** ADMIN/LÍDER inscrevendo outros membros no evento (ids escolhidos na tela). */
export function useInscreverMembros(eventoId: string) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (membroIds: string[]) =>
      inscricoesService.inscreverMembros(eventoId, { membroIds }),
    onSuccess: () => {
      invalidarCache(queryClient, 'inscricao')
      notificar.sucesso('Membros inscritos com sucesso!')
    },
    onError: (error: unknown) => {
      const mensagem = axios.isAxiosError<ApiError>(error)
        ? error.response?.data?.message
        : undefined
      notificar.erro('Não foi possível inscrever os membros', mensagem ?? 'Tente novamente.')
    },
  })
}
