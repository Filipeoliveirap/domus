import { useQuery, keepPreviousData } from '@tanstack/react-query'
import { categoriasService } from '@/services/financeiro/categoria.service'

interface UseCategoriasParams {
  q: string
  page: number
  size?: number
  enabled?: boolean
}

export function useCategorias({ q, page, size = 20, enabled = true }: UseCategoriasParams) {
  return useQuery({
    queryKey: ['categorias', { q, page, size }],
    queryFn: () => categoriasService.listar({ q, page, size }),
    placeholderData: keepPreviousData,
    enabled,
  })
}