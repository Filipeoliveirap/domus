import { useQuery } from '@tanstack/react-query'
import { relatorioService } from '@/services/financeiro/relatorio.service'
import type { PeriodoRelatorio } from '@/types/financeiro/relatorio.type'
import type { Vinculo } from '@/types/pessoa.type'

// igrejaId e vinculo entram na queryKey: sem isso o cache de uma igreja/filtro
// apareceria na tela de outra igreja ou de outro filtro.
export function usePorCategoria(periodo: PeriodoRelatorio, enabled = true, igrejaId?: string, vinculo?: Vinculo | '') {
  return useQuery({
    queryKey: ['relatorios', 'por-categoria', periodo, igrejaId ?? 'minha-igreja', vinculo || 'todos'],
    queryFn: () => relatorioService.porCategoria(periodo, igrejaId, vinculo),
    enabled,
  })
}