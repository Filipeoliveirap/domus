import axios from 'axios'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { notificar } from '@/components/common/Notificacao/notificar'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { inscricoesService } from '@/services/inscricao.service'
import type { ApiError } from '@/types/api.types'
import type { AcompanhanteRequest } from '@/types/inscricao.type'

// Chama /acompanhantes; "convidado" é só o nome na UI.
export function useAdicionarConvidado(eventoId: string, inscricaoId: string | null) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (data: AcompanhanteRequest) => {
      if (!inscricaoId) {
        throw new Error('Inscrição ainda não existe — inscreva-se antes de adicionar convidados.')
      }
      return inscricoesService.adicionarAcompanhante(eventoId, inscricaoId, data)
    },
    onSuccess: () => {
      invalidarCache(queryClient, 'inscricao')
      notificar.sucesso('Convidado adicionado!')
    },
    onError: (error: unknown) => {
      const mensagem = axios.isAxiosError<ApiError>(error)
        ? error.response?.data?.message
        : undefined
      notificar.erro('Não foi possível adicionar o convidado', mensagem ?? 'Tente novamente.')
    },
  })
}
