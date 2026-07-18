import { useQuery } from '@tanstack/react-query'
import { inicioService } from '@/services/inicio.service'

export function useInicio() {
  return useQuery({
    queryKey: ['inicio'],
    queryFn: () => inicioService.carregar(),
    staleTime: 5 * 60 * 1000,
  })
}
