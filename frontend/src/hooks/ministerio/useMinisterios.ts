import { useQuery } from '@tanstack/react-query'
import { ministerioService } from '@/services/ministerio.service'

export function useMinisterios() {
  return useQuery({
    queryKey: ['ministerios'],
    queryFn: () => ministerioService.listar(),
    staleTime: 60 * 1000,
  })
}
