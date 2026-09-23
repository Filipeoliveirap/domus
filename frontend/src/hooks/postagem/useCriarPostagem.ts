import { useMutation, useQueryClient } from '@tanstack/react-query'
import { postagemService, type CriarPostagemPayload } from '@/services/postagem.service'

export function useCriarPostagem() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (payload: CriarPostagemPayload) => postagemService.criar(payload),
    onSuccess: (_, variables) => {
      if (variables.oficial) {
        queryClient.invalidateQueries({ queryKey: ['mural-avisos'] })
      }
      queryClient.invalidateQueries({ queryKey: ['feed-postagens'] })
    },
  })
}
