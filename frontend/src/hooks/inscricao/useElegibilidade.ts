import { useQuery } from '@tanstack/react-query'
import { inscricoesService } from '@/services/inscricao.service'

// Só UX (desabilita o botão com o motivo antes do POST); o 422 do backend é quem decide de verdade.
export function useElegibilidade(eventoId: string | undefined) {
  return useQuery({
    queryKey: ['elegibilidade', eventoId],
    queryFn: () => inscricoesService.elegibilidade(eventoId!),
    enabled: !!eventoId,
  })
}
