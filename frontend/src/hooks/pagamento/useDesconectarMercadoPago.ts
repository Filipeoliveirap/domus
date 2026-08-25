import { useMutation, useQueryClient } from '@tanstack/react-query'
import { pagamentoService } from '@/services/pagamento.service'
import { notificar } from '@/components/common/Notificacao/notificar'
import { CHAVE_STATUS_PAGAMENTO } from './useContaPagamento'

export function useDesconectarMercadoPago() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: pagamentoService.desconectar,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: CHAVE_STATUS_PAGAMENTO })
      notificar.sucesso('Conta desconectada.')
    },
    onError: () => {
      notificar.erro('Não foi possível desconectar. Tente novamente.')
    },
  })
}
