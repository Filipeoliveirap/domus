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
  /**
   * Chamado quando o 422 é de elegibilidade e TODOS os impedimentos são contornáveis — o
   * caso em que um gestor pode se inscrever mesmo assim. Só quem PODE contornar (gestor)
   * passa este callback; com ele, o hook não notifica, porque a confirmação vai aparecer.
   * Sem ele (membro comum), o hook notifica o erro normalmente.
   */
  onContornavel?: (impedimentos: Impedimento[]) => void
}

/**
 * Auto-inscrição do próprio usuário no evento.
 *
 * <p><b>`silencioso`</b> desliga o aviso de sucesso, para o modo "Eu vou" (evento sem
 * inscrição prévia). Ali a interação é leve, como curtir um post: o feedback é o próprio
 * botão mudando de estado, e um toast dizendo "inscrição confirmada" seria pesado demais.
 *
 * <p>O ERRO continua avisando: falha silenciosa deixaria a pessoa achando que marcou
 * presença sem ter marcado. A exceção é o 422 contornável com `onContornavel` (gestor):
 * aí a confirmação "inscrever mesmo assim" toma o lugar do toast.
 */
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
