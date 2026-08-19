import { useQuery } from '@tanstack/react-query'
import { categoriasService } from '@/services/financeiro/categoria.service'

export function useContagemMovimentacoesCategoria(categoriaId: string) {
  return useQuery({
    queryKey: ['categoria-contagem-movimentacoes', categoriaId],
    queryFn: () => categoriasService.contarMovimentacoes(categoriaId),
  })
}
