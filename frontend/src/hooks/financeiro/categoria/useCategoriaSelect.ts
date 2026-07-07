import { useQuery } from '@tanstack/react-query'
import { categoriasService } from '@/services/financeiro/categoria.service'

export function useCategoriasSelect() {
  return useQuery({
    queryKey: ['categorias', 'todas'],
    queryFn: () => categoriasService.listarTodas(),
    staleTime: 1000 * 60 * 5,  
  })
}