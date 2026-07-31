import { useQuery } from '@tanstack/react-query'
import { movimentacoesService } from '@/services/financeiro/movimentacao.service'
import type { MovimentacaoFiltros } from '@/types/financeiro/movimentacao.type'

export function useMovimentacaoTotais(filtros: Omit<MovimentacaoFiltros, 'page' | 'size'>, enabled = true) {
  return useQuery({
    queryKey: ['movimentacoes', 'totais', filtros],
    queryFn: () => movimentacoesService.totais(filtros),
    enabled,
  })
}
