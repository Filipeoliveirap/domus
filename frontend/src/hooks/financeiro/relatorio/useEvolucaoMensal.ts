import { useQuery } from '@tanstack/react-query'
import { relatorioService } from '@/services/financeiro/relatorio.service'
import type { PeriodoRelatorio } from '@/types/financeiro/relatorio.type'

// igrejaId entra na queryKey: sem isso o cache de uma igreja apareceria na tela de outra.
export function useEvolucaoMensal(periodo: PeriodoRelatorio, enabled = true, igrejaId?: string) {
  return useQuery({
    queryKey: ['relatorios', 'evolucao-mensal', periodo, igrejaId ?? 'minha-igreja'],
    queryFn: () => relatorioService.evolucaoMensal(periodo, igrejaId),
    enabled,
  })
}