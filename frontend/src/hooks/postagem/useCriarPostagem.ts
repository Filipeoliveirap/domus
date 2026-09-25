import { useMutation, useQueryClient } from '@tanstack/react-query'
import { postagemService, type CriarPostagemPayload } from '@/services/postagem.service'
import type { Postagem } from '@/types/postagem.type'

export function useCriarPostagem() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationKey: ['criar-postagem'],
    mutationFn: (payload: CriarPostagemPayload) => postagemService.criar(payload),
    onSuccess: (novaPostagem, variables) => {
      if (variables.oficial) {
        queryClient.setQueryData<Postagem[]>(['mural-avisos'], (oldData) => {
          if (!oldData) return [novaPostagem]
          const jaExiste = oldData.some((p) => p.id === novaPostagem.id)
          return jaExiste ? oldData : [novaPostagem, ...oldData]
        })
        queryClient.invalidateQueries({ queryKey: ['mural-avisos'] })
      } else {
        queryClient.setQueriesData<{ content: Postagem[] }>(
          { queryKey: ['feed-postagens'] },
          (oldData) => {
            if (!oldData || !oldData.content) return oldData
            return {
              ...oldData,
              content: [novaPostagem, ...oldData.content],
            }
          },
        )
      }
      queryClient.invalidateQueries({ queryKey: ['feed-postagens'] })
    },
  })
}
