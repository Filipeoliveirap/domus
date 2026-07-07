import { useQuery, keepPreviousData } from '@tanstack/react-query'
import { movimentacoesService } from '@/services/financeiro/movimentacao.service'
import type { MovimentacaoFiltros } from '@/types/financeiro/movimentacao.type'

export function useMovimentacoes(filtros: MovimentacaoFiltros) {
  return useQuery({
    queryKey: ['movimentacoes', filtros],
    queryFn: () => movimentacoesService.listar(filtros),
    placeholderData: keepPreviousData,
  })
}