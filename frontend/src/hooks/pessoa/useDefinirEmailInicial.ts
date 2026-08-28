import axios from 'axios'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { notificar } from '@/components/common/Notificacao/notificar'
import { pessoasService } from '@/services/pessoa.service'
import type { ApiError } from '@/types/api.types'

/** Só pra dar o PRIMEIRO e-mail a uma Pessoa que ainda não tem — usado no fluxo de
 *  inscrição em evento (ver `ModalCompletarDadosInscricao`), tanto pra auto-inscrição
 *  quanto pra quando o admin está inscrevendo alguém sem e-mail cadastrado. */
export function useDefinirEmailInicial() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ pessoaId, email }: { pessoaId: string; email: string }) =>
      pessoasService.definirEmail(pessoaId, email),
    onSuccess: (resposta) => {
      queryClient.setQueryData(['pessoa', 'me'], (atual: unknown) =>
        atual && typeof atual === 'object' && (atual as { id?: string }).id === resposta.id ? resposta : atual)
      queryClient.invalidateQueries({ queryKey: ['pessoa'] })
      queryClient.invalidateQueries({ queryKey: ['pessoas'] })
    },
    onError: (error: unknown) => {
      const mensagem = axios.isAxiosError<ApiError>(error)
        ? error.response?.data?.message
        : undefined
      notificar.erro('Não foi possível salvar o e-mail', mensagem ?? 'Tente novamente.')
    },
  })
}
