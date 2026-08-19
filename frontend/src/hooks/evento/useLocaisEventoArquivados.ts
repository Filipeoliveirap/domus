import { useQuery } from '@tanstack/react-query'
import { locaisEventoService } from '@/services/localEvento.service'

export function useLocaisEventoArquivados() {
  return useQuery({
    queryKey: ['locais-evento-arquivados'],
    queryFn: () => locaisEventoService.listarArquivados(),
  })
}
