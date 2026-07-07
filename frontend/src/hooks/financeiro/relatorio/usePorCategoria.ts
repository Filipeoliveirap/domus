import { useQuery } from '@tanstack/react-query'
import { relatorioService } from '@/services/financeiro/relatorio.service'
import type { PeriodoRelatorio } from '@/types/financeiro/relatorio.type'

export function usePorCategoria(periodo: PeriodoRelatorio) {
  return useQuery({
    queryKey: ['relatorios', 'por-categoria', periodo],
    queryFn: () => relatorioService.porCategoria(periodo),
  })
}