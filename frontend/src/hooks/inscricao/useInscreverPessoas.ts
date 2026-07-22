import axios from 'axios'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { notificar } from '@/components/common/Notificacao/notificar'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { inscricoesService } from '@/services/inscricao.service'
import type { ApiError } from '@/types/api.types'

/**
 * ADMIN/LÍDER inscrevendo outras pessoas no evento (ids escolhidos na tela).
 *
 * O parâmetro chama-se `pessoaIds` porque é o nome do campo no contrato da API
 * (`InscreverPessoasRequest.pessoaIds`, rota `/inscricoes/pessoas`) — essa rota não foi
 * renomeada no backend, só a tabela/entidade (membro → pessoa).
 */
export function useInscreverPessoas(eventoId: string) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (pessoaIds: string[]) =>
      inscricoesService.inscreverPessoas(eventoId, { pessoaIds }),
    onSuccess: (_dados, pessoaIds) => {
      invalidarCache(queryClient, 'inscricao')
      const um = pessoaIds.length === 1
      notificar.sucesso(um ? 'Pessoa inscrita!' : `${pessoaIds.length} pessoas inscritas!`)
    },
    onError: (error: unknown, pessoaIds) => {
      const mensagem = axios.isAxiosError<ApiError>(error)
        ? error.response?.data?.message
        : undefined
      const titulo = pessoaIds.length === 1
        ? 'Não foi possível inscrever a pessoa'
        : 'Não foi possível inscrever as pessoas'
      notificar.erro(titulo, mensagem ?? 'Tente novamente.')
    },
  })
}
