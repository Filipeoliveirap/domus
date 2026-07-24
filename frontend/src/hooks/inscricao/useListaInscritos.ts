import { useQuery } from '@tanstack/react-query'
import { inscricoesService } from '@/services/inscricao.service'

/**
 * Lista completa de inscritos (telefone dos convidados, quem inscreveu quem). Restrita a
 * ADMIN/LÍDER no backend — um MEMBRO recebe 401. `enabled` existe justamente para o
 * componente decidir, pela role em sessão, se deve nem disparar a query.
 */
export function useListaInscritos(
  eventoId: string | undefined, enabled = true, busca = '', page = 0, size?: number,
) {
  return useQuery({
    queryKey: ['inscricoes', 'lista', eventoId, busca, page, size],
    queryFn: () => inscricoesService.listarInscritos(eventoId!, busca, page, size),
    enabled: !!eventoId && enabled,
  })
}
