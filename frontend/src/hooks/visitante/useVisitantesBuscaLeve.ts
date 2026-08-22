import { useQuery } from '@tanstack/react-query'
import { visitanteService } from '@/services/visitante.service'

export function useVisitantesBuscaLeve(q: string) {
  return useQuery({
    queryKey: ['visitantes-busca-leve', q],
    queryFn: () => visitanteService.buscaLeve(q),
    enabled: q.length >= 2,
  })
}
