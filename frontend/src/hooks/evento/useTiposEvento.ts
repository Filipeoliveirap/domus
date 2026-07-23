import { useQuery } from '@tanstack/react-query'
import { eventosService } from '@/services/evento.service'

// Tipos de evento já usados pela igreja (mais frequentes primeiro) + sementes. A ordem
// é decidida pelo backend — este hook não reordena.
export function useTiposEvento() {
  return useQuery({
    queryKey: ['eventos', 'tipos'],
    queryFn: () => eventosService.tipos(),
    staleTime: 5 * 60 * 1000,
  })
}
