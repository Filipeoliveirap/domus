import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { Endpoints } from '@/lib/endpoints'
import { notificar } from '@/components/common/Notificacao/notificar'
import type { AtualizarIgrejaRequest, IgrejaDetalhe } from '@/types/igreja/igreja.type'

const CHAVE = ['igreja', 'minha']

export function useMinhaIgreja(enabled = true) {
  return useQuery({
    queryKey: CHAVE,
    queryFn: async (): Promise<IgrejaDetalhe> => {
      const { data } = await api.get(Endpoints.igreja.MINHA)
      return data
    },
    enabled,
  })
}

export function useAtualizarIgreja() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async (body: AtualizarIgrejaRequest): Promise<IgrejaDetalhe> => {
      const { data } = await api.put(Endpoints.igreja.MINHA, body)
      return data
    },
    onSuccess: (igreja) => {
      // A resposta já traz o estado novo (inclusive a auditoria) — evita um GET extra.
      queryClient.setQueryData(CHAVE, igreja)
      notificar.sucesso('Configurações salvas', 'As informações da instituição foram atualizadas.')
    },
    onError: (erro: unknown) => {
      const mensagem =
        (erro as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'Tente novamente em alguns instantes.'
      notificar.erro('Não foi possível salvar', mensagem)
    },
  })
}
