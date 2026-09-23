import { useMutation, useQueryClient } from '@tanstack/react-query'
import { postagemService } from '@/services/postagem.service'
import type { TipoReacao } from '@/types/postagem.type'

export function useCurtirPostagem() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ postagemId, tipo }: { postagemId: string; tipo: TipoReacao }) =>
      postagemService.curtir(postagemId, tipo),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['feed-postagens'] })
      queryClient.invalidateQueries({ queryKey: ['mural-avisos'] })
    },
  })
}
