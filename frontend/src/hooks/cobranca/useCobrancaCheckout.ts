import { useQuery } from '@tanstack/react-query'
import { cobrancaService } from '@/services/cobranca.service'

/** Página de checkout dedicada (`/eventos/{eventoId}/pagamento/{cobrancaId}`) — pública
 *  pela mesma garantia de posse por UUID que o resto do módulo de cobrança (ver
 *  `useCobrancaPublica`), por isso `retry: false`. */
export function useCobrancaCheckout(cobrancaId: string) {
  return useQuery({
    queryKey: ['cobranca-checkout', cobrancaId],
    queryFn: () => cobrancaService.buscarPorId(cobrancaId),
    retry: false,
  })
}
