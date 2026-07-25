import { useQuery, keepPreviousData } from '@tanstack/react-query'
import { visitanteService } from '@/services/visitante.service'

interface UseVisitantesParams {
  q: string
  page: number
  size?: number
  contatoRealizado?: boolean
  visitaRealizada?: boolean
  acompanhamentoFeito?: boolean
}

export function useVisitantes({
  q, page, size = 20,
  contatoRealizado, visitaRealizada, acompanhamentoFeito,
}: UseVisitantesParams) {
  return useQuery({
    queryKey: ['visitantes', { q, page, size, contatoRealizado, visitaRealizada, acompanhamentoFeito }],
    queryFn: () => visitanteService.listar({
      q, page, size, contatoRealizado, visitaRealizada, acompanhamentoFeito,
    }),
    placeholderData: keepPreviousData,
  })
}
