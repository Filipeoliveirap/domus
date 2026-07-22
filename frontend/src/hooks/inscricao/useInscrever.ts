import axios from 'axios'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { notificar } from '@/components/common/Notificacao/notificar'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { inscricoesService } from '@/services/inscricao.service'
import type { ApiError } from '@/types/api.types'

/**
 * Auto-inscrição do próprio usuário no evento.
 *
 * <p><b>`silencioso`</b> desliga o aviso de sucesso, para o modo "Eu vou" (evento sem
 * inscrição prévia). Ali a interação é leve, como curtir um post: o feedback é o próprio
 * botão mudando de estado, e um toast dizendo "inscrição confirmada" seria errado duas
 * vezes — pesado demais para o gesto, e chamando de inscrição o que não é uma.
 *
 * <p>O ERRO continua avisando nos dois modos: falha silenciosa deixaria a pessoa achando
 * que marcou presença sem ter marcado.
 */
export function useInscrever(eventoId: string, silencioso = false) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: () => inscricoesService.inscrever(eventoId),
    onSuccess: () => {
      invalidarCache(queryClient, 'inscricao')
      if (!silencioso) notificar.sucesso('Inscrição confirmada!')
    },
    onError: (error: unknown) => {
      const mensagem = axios.isAxiosError<ApiError>(error)
        ? error.response?.data?.message
        : undefined
      notificar.erro('Não foi possível confirmar', mensagem ?? 'Tente novamente.')
    },
  })
}
