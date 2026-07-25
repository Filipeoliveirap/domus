import axios from 'axios'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { notificar } from '@/components/common/Notificacao/notificar'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { ministerioService } from '@/services/ministerio.service'
import { ROTULO_MINISTERIO } from '@/lib/rotulosMinisterio'
import type { ApiError } from '@/types/api.types'

function mensagemErro(error: unknown, fallback: string): string {
  return axios.isAxiosError<ApiError>(error) ? (error.response?.data?.message ?? fallback) : fallback
}

export function useAdicionarMembro(ministerioId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (pessoaId: string) => ministerioService.adicionarMembro(ministerioId, pessoaId),
    onSuccess: () => {
      invalidarCache(queryClient, 'ministerio')
      notificar.sucesso(`Pessoa adicionada à ${ROTULO_MINISTERIO.toLowerCase()}.`)
    },
    onError: (error: unknown) => notificar.erro('Não foi possível adicionar a pessoa', mensagemErro(error, 'Tente novamente.')),
  })
}

export function useRemoverMembro(ministerioId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (pessoaId: string) => ministerioService.removerMembro(ministerioId, pessoaId),
    onSuccess: () => {
      invalidarCache(queryClient, 'ministerio')
      notificar.sucesso('Membro removido.')
    },
    onError: (error: unknown) => notificar.erro('Não foi possível remover o membro', mensagemErro(error, 'Tente novamente.')),
  })
}

export function useAtualizarPapel(ministerioId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ pessoaId, papel }: { pessoaId: string; papel: 'LIDER' | 'MEMBRO' }) =>
      ministerioService.atualizarPapel(ministerioId, pessoaId, papel),
    onSuccess: () => {
      invalidarCache(queryClient, 'ministerio')
      notificar.sucesso('Papel atualizado.')
    },
    onError: (error: unknown) => notificar.erro('Não foi possível atualizar o papel', mensagemErro(error, 'Tente novamente.')),
  })
}
