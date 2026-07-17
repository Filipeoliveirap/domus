import { useQuery } from '@tanstack/react-query'
import { relatorioService } from '@/services/financeiro/relatorio.service'
import type { PeriodoRelatorio } from '@/types/financeiro/relatorio.type'

export function useMaiorLancamento(periodo: PeriodoRelatorio, enabled = true) {
  return useQuery({
    queryKey: ['relatorios', 'maior-lancamento', periodo],
    queryFn: () => relatorioService.maiorLancamento(periodo),
    enabled,
  })
}