import { useQuery } from '@tanstack/react-query'
import { relatorioService } from '@/services/financeiro/relatorio.service'
import type { PeriodoRelatorio } from '@/types/financeiro/relatorio.type'
import type { Vinculo } from '@/types/pessoa.type'

export function usePorContribuinte(periodo: PeriodoRelatorio, enabled = true, igrejaId?: string, vinculo?: Vinculo | '') {
  return useQuery({
    queryKey: ['relatorios', 'por-contribuinte', periodo, igrejaId ?? 'minha-igreja', vinculo || 'todos'],
    queryFn: () => relatorioService.porContribuinte(periodo, igrejaId, vinculo),
    enabled,
  })
}
