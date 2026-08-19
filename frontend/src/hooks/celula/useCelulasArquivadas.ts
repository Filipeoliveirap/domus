import { useQuery } from '@tanstack/react-query'
import { celulaService } from '@/services/celula.service'

export function useCelulasArquivadas() {
  return useQuery({
    queryKey: ['celulas-arquivadas'],
    queryFn: () => celulaService.listarArquivadas(),
  })
}
