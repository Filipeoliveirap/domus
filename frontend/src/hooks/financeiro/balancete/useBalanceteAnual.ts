import { useQuery } from '@tanstack/react-query'
import { balanceteService } from '@/services/financeiro/balancete.service'

export function useBalanceteAnual(ano: number, enabled = true) {
  return useQuery({
    queryKey: ['relatorios', 'balancete-anual', ano],
    queryFn: () => balanceteService.anual(ano),
    enabled,
  })
}
