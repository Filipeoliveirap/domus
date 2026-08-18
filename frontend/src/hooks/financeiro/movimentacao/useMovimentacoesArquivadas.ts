import { useQuery } from '@tanstack/react-query'
import { movimentacoesService } from '@/services/financeiro/movimentacao.service'

export function useMovimentacoesArquivadas() {
  return useQuery({
    queryKey: ['movimentacoes-arquivadas'],
    queryFn: () => movimentacoesService.listarArquivadas(),
  })
}
