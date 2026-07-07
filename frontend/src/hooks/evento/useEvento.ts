import { useQuery } from '@tanstack/react-query'
import { eventosService } from '@/services/evento.service'

export function useEvento(id: string | undefined) {
  return useQuery({
    queryKey: ['evento', id],
    queryFn: () => eventosService.buscar(id!),
    enabled: !!id,
  })
}