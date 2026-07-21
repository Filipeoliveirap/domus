import { useQuery } from '@tanstack/react-query'
import { inscricoesService } from '@/services/inscricao.service'

/** Se o próprio usuário está inscrito no evento, e seus convidados. */
export function useMinhaInscricao(eventoId: string | undefined) {
  return useQuery({
    queryKey: ['inscricoes', 'minha', eventoId],
    queryFn: () => inscricoesService.minhaInscricao(eventoId!),
    enabled: !!eventoId,
  })
}
