import { useQuery } from '@tanstack/react-query'
import { inscricoesService } from '@/services/inscricao.service'

// Restrita a ADMIN/LÍDER no backend (MEMBRO recebe 401); `enabled` deixa o componente decidir se dispara pela role em sessão.
export function useListaInscritos(
  eventoId: string | undefined, enabled = true, busca = '', page = 0, size?: number,
) {
  return useQuery({
    queryKey: ['inscricoes', 'lista', eventoId, busca, page, size],
    queryFn: () => inscricoesService.listarInscritos(eventoId!, busca, page, size),
    enabled: !!eventoId && enabled,
  })
}
