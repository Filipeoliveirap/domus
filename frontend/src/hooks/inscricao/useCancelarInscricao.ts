import axios from 'axios'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { notificar } from '@/components/common/Notificacao/notificar'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { inscricoesService } from '@/services/inscricao.service'
import type { ApiError } from '@/types/api.types'

/**
 * Cancela uma inscrição — a própria (pessoa) ou de outro (ADMIN/LÍDER); a regra é do backend.
 *
 * <p><b>`silencioso`</b> existe para o modo "Eu vou": desmarcar uma presença casual é como
 * descurtir um post — o botão voltando ao estado normal já é o feedback. Um toast dizendo
 * "inscrição cancelada" daria peso de cancelamento formal a um gesto que não é.
 */
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
