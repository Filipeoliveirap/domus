import { useQuery } from '@tanstack/react-query'
import { celulaService } from '@/services/celula.service'

export function useCelula(id: string | undefined) {
  return useQuery({
    queryKey: ['celulas', id],
    queryFn: () => celulaService.buscar(id!),
    enabled: !!id,
  })
}
