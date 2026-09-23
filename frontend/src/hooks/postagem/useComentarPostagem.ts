import { useMutation, useQueryClient } from '@tanstack/react-query'
import { postagemService } from '@/services/postagem.service'

export function useComentarPostagem() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ postagemId, conteudo }: { postagemId: string; conteudo: string }) =>
      postagemService.comentar(postagemId, conteudo),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['feed-postagens'] })
      queryClient.invalidateQueries({ queryKey: ['mural-avisos'] })
    },
  })
}
