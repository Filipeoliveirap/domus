import axios from 'axios'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { notificar } from '@/components/common/Notificacao/notificar'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { inscricoesService } from '@/services/inscricao.service'
import type { ApiError } from '@/types/api.types'
import type { Impedimento } from '@/types/inscricao.type'

interface Variaveis {
  pessoaIds: string[]
  /** `true` = "inscrever mesmo assim" — só tem efeito para quem gerencia (backend decide). */
  confirmado?: boolean
}

/** Lista de impedimentos do 422 de elegibilidade (`NAO_ELEGIVEL`), se for esse o erro. */
export function impedimentosDe422(error: unknown): Impedimento[] | undefined {
  if (!axios.isAxiosError<ApiError>(error) || error.response?.status !== 422) return undefined
  return error.response.data.impedimentos
}

/** Se o 422 é de elegibilidade com ao menos um impedimento contornável. */
export function ehNaoElegivelContornavel(error: unknown): boolean {
  return !!impedimentosDe422(error)?.some((i) => i.contornavel)
}

/**
 * ADMIN/LÍDER inscrevendo outras pessoas no evento (ids escolhidos na tela).
 *
 * O parâmetro chama-se `pessoaIds` porque é o nome do campo no contrato da API
 * (`InscreverPessoasRequest.pessoaIds`, rota `/inscricoes/pessoas`) — essa rota não foi
 * renomeada no backend, só a tabela/entidade (membro → pessoa).
 *
 * <p>Quando o 422 tem impedimento CONTORNÁVEL, este hook não notifica sozinho — quem chama
 * decide (mostrar a confirmação "inscrever mesmo assim" e reenviar com `confirmado=true`).
 * Nos demais erros, notifica normalmente.
 */
export function useInscreverPessoas(eventoId: string) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ pessoaIds, confirmado }: Variaveis) =>
      inscricoesService.inscreverPessoas(eventoId, { pessoaIds }, confirmado),
    onSuccess: (_dados, { pessoaIds }) => {
      invalidarCache(queryClient, 'inscricao')
      const um = pessoaIds.length === 1
      notificar.sucesso(um ? 'Pessoa inscrita!' : `${pessoaIds.length} pessoas inscritas!`)
    },
    onError: (error: unknown, { pessoaIds }) => {
      if (ehNaoElegivelContornavel(error)) return // quem chamou vai mostrar a confirmação

      const impedimentos = impedimentosDe422(error)
      const mensagem = impedimentos?.length
        ? impedimentos.map((i) => i.mensagem).join(' ')
        : axios.isAxiosError<ApiError>(error) ? error.response?.data?.message : undefined

      const titulo = pessoaIds.length === 1
        ? 'Não foi possível inscrever a pessoa'
        : 'Não foi possível inscrever as pessoas'
      notificar.erro(titulo, mensagem ?? 'Tente novamente.')
    },
  })
}
