import axios from 'axios'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { notificar } from '@/components/common/Notificacao/notificar'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { ministerioService } from '@/services/ministerio.service'
import type { MinisterioRequest } from '@/types/ministerio.type'
import type { ApiError } from '@/types/api.types'

function mensagemErro(error: unknown, fallback: string): string {
  return axios.isAxiosError<ApiError>(error) ? (error.response?.data?.message ?? fallback) : fallback
}

export function useCriarMinisterio() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: MinisterioRequest) => ministerioService.criar(data),
    onSuccess: () => {
      invalidarCache(queryClient, 'ministerio')
      notificar.sucesso('Ministério criado.')
    },
    onError: (error: unknown) => notificar.erro('Não foi possível criar o ministério', mensagemErro(error, 'Tente novamente.')),
  })
}

export function useAtualizarMinisterio(id: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: MinisterioRequest) => ministerioService.atualizar(id, data),
    onSuccess: () => {
      invalidarCache(queryClient, 'ministerio')
      notificar.sucesso('Ministério atualizado.')
    },
    onError: (error: unknown) => notificar.erro('Não foi possível atualizar o ministério', mensagemErro(error, 'Tente novamente.')),
  })
}

export function useArquivarMinisterio() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => ministerioService.arquivar(id),
    onSuccess: () => {
      invalidarCache(queryClient, 'ministerio')
      notificar.sucesso('Ministério arquivado.')
    },
    onError: (error: unknown) => notificar.erro('Não foi possível arquivar o ministério', mensagemErro(error, 'Tente novamente.')),
  })
}
