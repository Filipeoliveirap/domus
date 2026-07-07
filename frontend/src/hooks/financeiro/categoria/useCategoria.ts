import { useQuery } from '@tanstack/react-query'
import { categoriasService } from '@/services/financeiro/categoria.service'

export function useCategoria(id: string | undefined) {
  return useQuery({
    queryKey: ['categoria', id],
    queryFn: () => categoriasService.buscar(id!),
    enabled: !!id,
  })
}