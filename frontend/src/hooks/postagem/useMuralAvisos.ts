import { useQuery } from '@tanstack/react-query'
import { postagemService } from '@/services/postagem.service'

export function useMuralAvisos() {
  return useQuery({
    queryKey: ['mural-avisos'],
    queryFn: () => postagemService.listarMural(),
    staleTime: 5 * 60 * 1000,
  })
}
