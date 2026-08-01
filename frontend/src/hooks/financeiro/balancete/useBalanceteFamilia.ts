import { useQuery } from '@tanstack/react-query'
import { balanceteService } from '@/services/financeiro/balancete.service'

export function useBalanceteFamilia(ano: number, enabled = true) {
  return useQuery({
    queryKey: ['relatorios', 'balancete-anual', 'familia', ano],
    queryFn: () => balanceteService.familia(ano),
    enabled,
  })
}
