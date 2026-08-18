import axios from 'axios'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { notificar } from '@/components/common/Notificacao/notificar'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { inscricoesService } from '@/services/inscricao.service'
import { impedimentosDe422, ehNaoElegivelContornavel } from '@/hooks/inscricao/useInscreverPessoas'
import type { ApiError } from '@/types/api.types'
import type { Impedimento } from '@/types/inscricao.type'

interface Variaveis {
  /** `true` = "inscrever mesmo assim" — só surte efeito para quem gerencia (backend decide). */
  confirmado?: boolean
}

interface Opcoes {
  // Presente = hook não notifica o 422 (abre confirmação em vez de erro); ausente = notifica normalmente.
  onContornavel?: (impedimentos: Impedimento[]) => void
}

export function useInscrever(eventoId: string, silencioso = false, opcoes: Opcoes = {}) {
  const queryClient = useQueryClient()

  return useMutation({
    // `vars?` (opcional) mantém `.mutate()` sem argumento válido nos chamadores antigos
    // do modo "Eu vou", ao mesmo tempo que aceita `.mutate({ confirmado: true })`.
    mutationFn: (vars?: Variaveis) => inscricoesService.inscrever(eventoId, vars?.confirmado),
    onSuccess: () => {
      invalidarCache(queryClient, 'inscricao')
      if (!silencioso) notificar.sucesso('Inscrição confirmada!')
    },
    onError: (error: unknown) => {
      // Gestor: 422 totalmente contornável abre a confirmação, não um toast de erro.
      if (opcoes.onContornavel && ehNaoElegivelContornavel(error)) {
        opcoes.onContornavel(impedimentosDe422(error)!)
        return
      }
      const mensagem = axios.isAxiosError<ApiError>(error)
        ? error.response?.data?.message
        : undefined
      notificar.erro('Não foi possível confirmar', mensagem ?? 'Tente novamente.')
    },
  })
}
