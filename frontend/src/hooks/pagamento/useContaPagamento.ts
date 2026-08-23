import { useQuery } from '@tanstack/react-query'
import { pagamentoService } from '@/services/pagamento.service'

export const CHAVE_STATUS_PAGAMENTO = ['pagamento', 'status']

export function useContaPagamento() {
  return useQuery({
    queryKey: CHAVE_STATUS_PAGAMENTO,
    queryFn: pagamentoService.buscarStatus,
  })
}
