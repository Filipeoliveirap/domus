import { useQuery } from '@tanstack/react-query'
import { relatorioService } from '@/services/financeiro/relatorio.service'
import type { PeriodoRelatorio } from '@/types/financeiro/relatorio.type'

export function useResumo(periodo: PeriodoRelatorio) {
  return useQuery({
    queryKey: ['relatorios', 'resumo', periodo],
    queryFn: () => relatorioService.resumo(periodo),
  })
}