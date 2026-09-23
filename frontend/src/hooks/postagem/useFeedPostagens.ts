import { useQuery } from '@tanstack/react-query'
import { postagemService } from '@/services/postagem.service'
import type { TipoPostagem } from '@/types/postagem.type'

export function useFeedPostagens(tipoFilter?: TipoPostagem, page = 0) {
  return useQuery({
    queryKey: ['feed-postagens', tipoFilter ?? 'TUDO', page],
    queryFn: () => postagemService.listarFeed(tipoFilter, page),
    staleTime: 2 * 60 * 1000,
  })
}
