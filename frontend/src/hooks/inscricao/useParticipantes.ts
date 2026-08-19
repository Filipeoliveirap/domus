import { useQuery } from '@tanstack/react-query'
import { inscricoesService } from '@/services/inscricao.service'

// Lista reduzida, qualquer autenticado pode ver; `enabled` evita rodar junto com a lista completa de gestor.
export function useParticipantes(eventoId: string | undefined, enabled = true) {
  return useQuery({
    queryKey: ['inscricoes', 'participantes', eventoId],
    queryFn: () => inscricoesService.participantes(eventoId!),
    enabled: !!eventoId && enabled,
  })
}
