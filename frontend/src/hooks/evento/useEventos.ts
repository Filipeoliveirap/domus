import { useQuery, keepPreviousData } from '@tanstack/react-query'
import { eventosService } from '@/services/evento.service'

interface UseEventosParams {
  q: string
  page: number
  size?: number
  tipo?: string
  recorteEtario?: string
}

export function useEventos({ q, page, size = 12, tipo, recorteEtario }: UseEventosParams) {
  return useQuery({
    queryKey: ['eventos', { q, page, size, tipo, recorteEtario }],
    queryFn: () => eventosService.listar({ q, page, size, tipo, recorteEtario }),
    placeholderData: keepPreviousData,
  })
}
