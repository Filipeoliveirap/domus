import { useQuery } from '@tanstack/react-query'
import { ministerioService } from '@/services/ministerio.service'

export function useMinisterioDetalhe(id: string) {
  return useQuery({
    queryKey: ['ministerios', id],
    queryFn: () => ministerioService.detalhe(id),
    enabled: !!id,
  })
}
