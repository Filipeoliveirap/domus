import { useQuery } from '@tanstack/react-query'
import { visitanteService } from '@/services/visitante.service'

export function useVisitante(id: string | undefined) {
  return useQuery({
    queryKey: ['visitante', id],
    queryFn: () => visitanteService.buscar(id!),
    enabled: !!id,
  })
}
