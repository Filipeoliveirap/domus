import axios from 'axios'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { notificar } from '@/components/common/Notificacao/notificar'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { ministerioService } from '@/services/ministerio.service'
import { useRotulos } from '@/lib/rotulos/useRotulos'
import type { ApiError } from '@/types/api.types'

function mensagemErro(error: unknown, fallback: string): string {
  return axios.isAxiosError<ApiError>(error) ? (error.response?.data?.message ?? fallback) : fallback
}

export function useAdicionarMembro(ministerioId: string) {
  const queryClient = useQueryClient()
  const { ministerio } = useRotulos()
  return useMutation({
    mutationFn: (pessoaId: string) => ministerioService.adicionarMembro(ministerioId, pessoaId),
    onSuccess: () => {
      invalidarCache(queryClient, 'ministerio')
      // "em X" em vez de "à X" evita depender de crase, que só funciona pro artigo feminino.
      notificar.sucesso(`Pessoa adicionada em ${ministerio.singular.toLowerCase()}.`)
    },
    onError: (error: unknown) => notificar.erro('Não foi possível adicionar a pessoa', mensagemErro(error, 'Tente novamente.')),
  })
}

export function useRemoverMembro(ministerioId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (pessoaId: string) => ministerioService.removerMembro(ministerioId, pessoaId),
    onSuccess: () => {
      // Sem toast de sucesso: a linha colapsa animada, já fica claro que saiu.
      invalidarCache(queryClient, 'ministerio')
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
      // Sem toast de sucesso: o badge de líder entra/sai animado, já é visível.
      invalidarCache(queryClient, 'ministerio')
    },
    onError: (error: unknown) => notificar.erro('Não foi possível atualizar o papel', mensagemErro(error, 'Tente novamente.')),
  })
}
