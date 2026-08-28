import axios from 'axios'
import { useMutation } from '@tanstack/react-query'
import { notificar } from '@/components/common/Notificacao/notificar'
import { inscricoesService } from '@/services/inscricao.service'
import type { ApiError } from '@/types/api.types'

export function useEnviarLembretePagamento(eventoId: string) {
  return useMutation({
    mutationFn: (inscricaoId: string) => inscricoesService.enviarLembretePagamento(eventoId, inscricaoId),
    onSuccess: () => notificar.sucesso('Lembrete enviado por e-mail.'),
    onError: (error: unknown) => {
      const mensagem = axios.isAxiosError<ApiError>(error)
        ? error.response?.data?.message
        : undefined
      notificar.erro('Não foi possível enviar o lembrete', mensagem ?? 'Tente novamente.')
    },
  })
}
