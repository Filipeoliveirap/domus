import { useQuery } from '@tanstack/react-query'
import { celulaService } from '@/services/celula.service'

export function useCelulas() {
  return useQuery({
    queryKey: ['celulas'],
    queryFn: () => celulaService.listar(),
  })
}
