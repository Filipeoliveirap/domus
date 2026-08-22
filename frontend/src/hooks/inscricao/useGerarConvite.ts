import axios from 'axios'
import { useMutation } from '@tanstack/react-query'
import { notificar } from '@/components/common/Notificacao/notificar'
import { conviteService } from '@/services/convite.service'
import type { ApiError } from '@/types/api.types'

export function useGerarConvite(eventoId: string) {
  return useMutation({
    mutationFn: () => conviteService.gerar(eventoId),
    onError: (error: unknown) => {
      const mensagem = axios.isAxiosError<ApiError>(error)
        ? error.response?.data?.message
        : undefined
      notificar.erro('Não foi possível gerar o link', mensagem ?? 'Tente novamente.')
    },
  })
}
