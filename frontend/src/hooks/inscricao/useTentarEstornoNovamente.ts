import axios from 'axios'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { notificar } from '@/components/common/Notificacao/notificar'
import { cobrancaService } from '@/services/cobranca.service'
import type { ApiError } from '@/types/api.types'

/** Retry manual da tag "Estorno pendente" (2026-08-27) — invalida a lista de inscritos no
 *  sucesso pra tag sumir sem precisar de reload manual. */
export function useTentarEstornoNovamente(eventoId: string) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (cobrancaId: string) => cobrancaService.tentarEstornoNovamente(cobrancaId),
    onSuccess: () => {
      notificar.sucesso('Estorno concluído.')
      queryClient.invalidateQueries({ queryKey: ['inscricoes', 'lista', eventoId] })
    },
    onError: (error: unknown) => {
      const mensagem = axios.isAxiosError<ApiError>(error)
        ? error.response?.data?.message
        : undefined
      notificar.erro('Não foi possível estornar', mensagem ?? 'Tente novamente mais tarde.')
    },
  })
}
