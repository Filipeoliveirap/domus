import { useMutation, useQueryClient } from '@tanstack/react-query'
import { postagemService } from '@/services/postagem.service'
import type { Postagem } from '@/types/postagem.type'

export function useDeletarPostagem() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (postagemId: string) => postagemService.deletar(postagemId),
    onMutate: async (postagemId) => {
      await queryClient.cancelQueries({ queryKey: ['feed-postagens'] })
      await queryClient.cancelQueries({ queryKey: ['mural-avisos'] })

      queryClient.setQueriesData<{ content: Postagem[] }>(
        { queryKey: ['feed-postagens'] },
        (oldData) => {
          if (!oldData || !oldData.content) return oldData
          return {
            ...oldData,
            content: oldData.content.filter((p) => p.id !== postagemId),
          }
        },
      )

      queryClient.setQueriesData<Postagem[]>(
        { queryKey: ['mural-avisos'] },
        (oldData) => (oldData ? oldData.filter((p) => p.id !== postagemId) : oldData),
      )
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ['feed-postagens'] })
      queryClient.invalidateQueries({ queryKey: ['mural-avisos'] })
    },
  })
}
