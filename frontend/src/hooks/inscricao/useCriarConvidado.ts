import axios from 'axios'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { notificar } from '@/components/common/Notificacao/notificar'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { inscricoesService } from '@/services/inscricao.service'
import type { ApiError } from '@/types/api.types'
import type { CriarConvidadoRequest } from '@/types/inscricao.type'

/** Convidado sem cadastro ganha inscrição própria (não acompanhante aninhado) — usado pelo
 *  modal unificado "Inscrever alguém" (abas Visitantes/Pessoa de fora), nunca pelo antigo
 *  "Vou levar alguém de fora" (esse continua em useAdicionarConvidado). */
export function useCriarConvidado(eventoId: string) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (data: CriarConvidadoRequest) => inscricoesService.criarConvidado(eventoId, data),
    onSuccess: () => {
      invalidarCache(queryClient, 'inscricao')
      notificar.sucesso('Convidado inscrito!')
    },
    onError: (error: unknown) => {
      const mensagem = axios.isAxiosError<ApiError>(error)
        ? error.response?.data?.message
        : undefined
      notificar.erro('Não foi possível inscrever o convidado', mensagem ?? 'Tente novamente.')
    },
  })
}
