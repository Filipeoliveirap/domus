import { useQuery, keepPreviousData } from '@tanstack/react-query'
import { eventosService } from '@/services/evento.service'

interface UseEventosParams {
  q: string
  page: number
  size?: number
}

export function useEventos({ q, page, size = 12 }: UseEventosParams) {
  return useQuery({
    queryKey: ['eventos', { q, page, size }],
    queryFn: () => eventosService.listar({ q, page, size }),
    placeholderData: keepPreviousData,
  })
}