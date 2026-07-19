import { useQuery } from '@tanstack/react-query'
import { relatorioService } from '@/services/financeiro/relatorio.service'
import type { PeriodoRelatorio } from '@/types/financeiro/relatorio.type'

// igrejaId entra na queryKey: sem isso o cache de uma igreja apareceria na tela de outra.
export function usePorCategoria(periodo: PeriodoRelatorio, enabled = true, igrejaId?: string) {
  return useQuery({
    queryKey: ['relatorios', 'por-categoria', periodo, igrejaId ?? 'minha-igreja'],
    queryFn: () => relatorioService.porCategoria(periodo, igrejaId),
    enabled,
  })
}