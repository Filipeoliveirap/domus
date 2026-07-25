import axios from 'axios'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { notificar } from '@/components/common/Notificacao/notificar'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { ministerioService } from '@/services/ministerio.service'
import type { ApiError } from '@/types/api.types'

function mensagemErro(error: unknown, fallback: string): string {
  return axios.isAxiosError<ApiError>(error) ? (error.response?.data?.message ?? fallback) : fallback
}

export function usePedirEntrada(ministerioId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => ministerioService.pedirEntrada(ministerioId),
    onSuccess: () => {
      invalidarCache(queryClient, 'ministerio')
      notificar.sucesso('Pedido enviado. Aguarde a aprovação do líder.')
    },
    onError: (error: unknown) => notificar.erro('Não foi possível enviar o pedido', mensagemErro(error, 'Tente novamente.')),
  })
}

export function useAceitarPedido(ministerioId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (pessoaId: string) => ministerioService.aceitarPedido(ministerioId, pessoaId),
    onSuccess: () => {
      invalidarCache(queryClient, 'ministerio')
      notificar.sucesso('Pedido aceito.')
    },
    onError: (error: unknown) => notificar.erro('Não foi possível aceitar o pedido', mensagemErro(error, 'Tente novamente.')),
  })
}

export function useRecusarPedido(ministerioId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (pessoaId: string) => ministerioService.recusarPedido(ministerioId, pessoaId),
    onSuccess: () => {
      invalidarCache(queryClient, 'ministerio')
      notificar.sucesso('Pedido recusado.')
    },
    onError: (error: unknown) => notificar.erro('Não foi possível recusar o pedido', mensagemErro(error, 'Tente novamente.')),
  })
}
