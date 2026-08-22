import { useQuery } from '@tanstack/react-query'
import { conviteService } from '@/services/convite.service'

export function useConvitePublico(token: string) {
  return useQuery({
    queryKey: ['convite-publico', token],
    queryFn: () => conviteService.consultar(token),
    retry: false,
  })
}
