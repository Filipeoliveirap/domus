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
    onSuccess: (_dados, membroIds) => {
      invalidarCache(queryClient, 'inscricao')
      const um = membroIds.length === 1
      notificar.sucesso(um ? 'Membro inscrito!' : `${membroIds.length} membros inscritos!`)
    },
    onError: (error: unknown, membroIds) => {
      const mensagem = axios.isAxiosError<ApiError>(error)
        ? error.response?.data?.message
        : undefined
      const titulo = membroIds.length === 1
        ? 'Não foi possível inscrever o membro'
        : 'Não foi possível inscrever os membros'
      notificar.erro(titulo, mensagem ?? 'Tente novamente.')
    },
  })
}
