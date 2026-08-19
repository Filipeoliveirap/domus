import { useQuery } from '@tanstack/react-query'
import { ministerioService } from '@/services/ministerio.service'

export function useMinisteriosArquivados() {
  return useQuery({
    queryKey: ['ministerios-arquivados'],
    queryFn: () => ministerioService.listarArquivadas(),
  })
}
