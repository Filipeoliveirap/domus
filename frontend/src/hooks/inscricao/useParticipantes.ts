import { useQuery } from '@tanstack/react-query'
import { inscricoesService } from '@/services/inscricao.service'

/**
 * Lista reduzida de "quem vai" — qualquer pessoa autenticada pode ver.
 *
 * <p>O `enabled` permite ao chamador desligar a consulta quando já vai buscar a lista
 * completa de gestor, evitando as duas rodarem para a mesma tela.
 */
export function useParticipantes(eventoId: string | undefined, enabled = true) {
  return useQuery({
    queryKey: ['inscricoes', 'participantes', eventoId],
    queryFn: () => inscricoesService.participantes(eventoId!),
    enabled: !!eventoId && enabled,
  })
}
