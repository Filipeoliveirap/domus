import { useQuery } from '@tanstack/react-query'
import { movimentacoesService } from '@/services/financeiro/movimentacao.service'

export function useMovimentacao(id: string | undefined) {
  return useQuery({
    queryKey: ['movimentacao', id],
    queryFn: () => movimentacoesService.buscar(id!),
    enabled: !!id,
  })
}