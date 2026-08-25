import axios from 'axios'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { notificar } from '@/components/common/Notificacao/notificar'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { ministerioService } from '@/services/ministerio.service'
import { useRotulos } from '@/lib/rotulos/useRotulos'
import type { MinisterioRequest } from '@/types/ministerio.type'
import type { ApiError } from '@/types/api.types'

function mensagemErro(error: unknown, fallback: string): string {
  return axios.isAxiosError<ApiError>(error) ? (error.response?.data?.message ?? fallback) : fallback
}

// Particípio ("criada"/"atualizada"/"arquivada") fica fixo em feminino de propósito — mesma
// limitação conhecida e aceita do toast de Célula (v1, YAGNI): cobrir a concordância completa
// do particípio pro rótulo custom fica pra uma iteração futura, se algum dia for pedido.
export function useCriarMinisterio() {
  const queryClient = useQueryClient()
  const { ministerio } = useRotulos()
  return useMutation({
    mutationFn: (data: MinisterioRequest) => ministerioService.criar(data),
    onSuccess: () => {
      invalidarCache(queryClient, 'ministerio')
      notificar.sucesso(`${ministerio.singular} criada.`)
    },
    onError: (error: unknown) => notificar.erro(`Não foi possível criar a ${ministerio.singular.toLowerCase()}`, mensagemErro(error, 'Tente novamente.')),
  })
}

export function useAtualizarMinisterio(id: string) {
  const queryClient = useQueryClient()
  const { ministerio } = useRotulos()
  return useMutation({
    mutationFn: (data: MinisterioRequest) => ministerioService.atualizar(id, data),
    onSuccess: () => {
      invalidarCache(queryClient, 'ministerio')
      notificar.sucesso(`${ministerio.singular} atualizada.`)
    },
    onError: (error: unknown) => notificar.erro(`Não foi possível atualizar a ${ministerio.singular.toLowerCase()}`, mensagemErro(error, 'Tente novamente.')),
  })
}

/**
 * Salva só a foto — dispara assim que o UploadFoto confirma o recorte (ou
 * remove a foto), sem esperar o resto do modal ser salvo. Só usada em edição.
 */
export function useAtualizarFotoMinisterio(id: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (fotoId: string | null) => ministerioService.atualizarFoto(id, fotoId),
    onSuccess: () => invalidarCache(queryClient, 'ministerio'),
  })
}

export function useArquivarMinisterio() {
  const queryClient = useQueryClient()
  const { ministerio } = useRotulos()
  return useMutation({
    mutationFn: (id: string) => ministerioService.arquivar(id),
    onSuccess: () => {
      invalidarCache(queryClient, 'ministerio')
      notificar.sucesso(`${ministerio.singular} arquivada.`)
    },
    onError: (error: unknown) => notificar.erro(`Não foi possível arquivar a ${ministerio.singular.toLowerCase()}`, mensagemErro(error, 'Tente novamente.')),
  })
}
