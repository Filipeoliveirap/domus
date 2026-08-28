import axios from 'axios'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { notificar } from '@/components/common/Notificacao/notificar'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { inscricoesService } from '@/services/inscricao.service'
import type { ApiError } from '@/types/api.types'
import type { CriarConvidadoRequest } from '@/types/inscricao.type'

/** Convidado sem cadastro ganha inscrição própria (não acompanhante aninhado) — usado pelo
 *  modal unificado "Inscrever alguém" (abas Visitantes/Pessoa de fora). */
export function useCriarConvidado(eventoId: string) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (data: CriarConvidadoRequest) => inscricoesService.criarConvidado(eventoId, data),
    onSuccess: (resposta) => {
      invalidarCache(queryClient, 'inscricao')
      // Evento pago: a inscrição fica AGUARDANDO_PAGAMENTO — quem sinaliza o próximo passo
      // é a navegação pro checkout ou o modal de compartilhar link, não um toast de
      // "inscrito" que soaria como confirmado antes de qualquer pagamento acontecer.
      if (!resposta.cobrancaId) {
        notificar.sucesso('Convidado inscrito!')
      }
    },
    onError: (error: unknown) => {
      const mensagem = axios.isAxiosError<ApiError>(error)
        ? error.response?.data?.message
        : undefined
      notificar.erro('Não foi possível inscrever o convidado', mensagem ?? 'Tente novamente.')
    },
  })
}
