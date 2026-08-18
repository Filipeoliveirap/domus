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

interface Opcoes {
  // Presente = hook não notifica o 422 (abre confirmação em vez de erro); ausente = notifica normalmente.
  onContornavel?: (impedimentos: Impedimento[]) => void
}

/** Lista de impedimentos do 422 de elegibilidade (`NAO_ELEGIVEL`), se for esse o erro. */
export function impedimentosDe422(error: unknown): Impedimento[] | undefined {
  if (!axios.isAxiosError<ApiError>(error) || error.response?.status !== 422) return undefined
  return error.response.data.impedimentos
}

// TODOS os impedimentos precisam ser contornáveis (espelha totalmenteContornavel() do backend); `.some` ofereceria contorno que o backend rejeitaria de novo.
export function ehNaoElegivelContornavel(error: unknown): boolean {
  const imps = impedimentosDe422(error)
  return !!imps?.length && imps.every((i) => i.contornavel)
}

export function useInscreverPessoas(eventoId: string, opcoes: Opcoes = {}) {
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
      // Só fica quieto se HÁ quem trate o contorno (gestor passou o callback) E o 422 é
      // totalmente contornável. Sem isso, um não-gestor levava o 422 em silêncio — a
      // confirmação nunca aparecia para ele, e o erro sumia.
      if (opcoes.onContornavel && ehNaoElegivelContornavel(error)) {
        opcoes.onContornavel(impedimentosDe422(error)!)
        return
      }

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
