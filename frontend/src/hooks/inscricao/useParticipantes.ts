import { useQuery } from '@tanstack/react-query'
import { inscricoesService } from '@/services/inscricao.service'

/** Lista reduzida de "quem vai" — qualquer membro autenticado pode ver. */
export function useParticipantes(eventoId: string | undefined) {
  return useQuery({
    queryKey: ['inscricoes', 'participantes', eventoId],
    queryFn: () => inscricoesService.participantes(eventoId!),
    enabled: !!eventoId,
  })
}
