import { useQuery } from '@tanstack/react-query'
import { membrosService } from '@/services/membro.service'

export function useMembro(id: string | undefined) {
  return useQuery({
    queryKey: ['membro', id],
    queryFn: () => membrosService.buscar(id!),
    enabled: !!id,  
  })
}