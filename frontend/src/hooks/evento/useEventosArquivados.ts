import { useQuery } from '@tanstack/react-query'
import { eventosService } from '@/services/evento.service'

export function useEventosArquivados() {
  return useQuery({
    queryKey: ['eventos-arquivados'],
    queryFn: () => eventosService.listarArquivados(),
  })
}
