import axios from 'axios'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { notificar } from '@/components/common/Notificacao/notificar'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { ministerioService } from '@/services/ministerio.service'
import { ROTULO_MINISTERIO } from '@/lib/rotulosMinisterio'
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
      notificar.sucesso(`${ROTULO_MINISTERIO} criada.`)
    },
    onError: (error: unknown) => notificar.erro(`Não foi possível criar a ${ROTULO_MINISTERIO.toLowerCase()}`, mensagemErro(error, 'Tente novamente.')),
  })
}

export function useAtualizarMinisterio(id: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: MinisterioRequest) => ministerioService.atualizar(id, data),
    onSuccess: () => {
      invalidarCache(queryClient, 'ministerio')
      notificar.sucesso(`${ROTULO_MINISTERIO} atualizada.`)
    },
    onError: (error: unknown) => notificar.erro(`Não foi possível atualizar a ${ROTULO_MINISTERIO.toLowerCase()}`, mensagemErro(error, 'Tente novamente.')),
  })
}

export function useArquivarMinisterio() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => ministerioService.arquivar(id),
    onSuccess: () => {
      invalidarCache(queryClient, 'ministerio')
      notificar.sucesso(`${ROTULO_MINISTERIO} arquivada.`)
    },
    onError: (error: unknown) => notificar.erro(`Não foi possível arquivar a ${ROTULO_MINISTERIO.toLowerCase()}`, mensagemErro(error, 'Tente novamente.')),
  })
}
