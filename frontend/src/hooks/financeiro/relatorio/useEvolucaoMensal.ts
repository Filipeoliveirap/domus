import { useQuery } from '@tanstack/react-query'
import { relatorioService } from '@/services/financeiro/relatorio.service'
import type { PeriodoRelatorio } from '@/types/financeiro/relatorio.type'

export function useEvolucaoMensal(periodo: PeriodoRelatorio) {
  return useQuery({
    queryKey: ['relatorios', 'evolucao-mensal', periodo],
    queryFn: () => relatorioService.evolucaoMensal(periodo),
  })
}