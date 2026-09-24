import { useMutation, useQueryClient } from '@tanstack/react-query'
import { postagemService } from '@/services/postagem.service'
import type { Postagem } from '@/types/postagem.type'

export function useDeletarComentario() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ postagemId, comentarioId }: { postagemId: string; comentarioId: string }) =>
      postagemService.deletarComentario(postagemId, comentarioId),
    onSuccess: (_, { postagemId, comentarioId }) => {
      const remover = (post: Postagem): Postagem => {
        if (post.id !== postagemId) return post
        const comentarios = (post.comentariosRecentes ?? []).filter((c) => c.id !== comentarioId)
        return {
          ...post,
          totalComentarios: Math.max(0, post.totalComentarios - 1),
          comentariosRecentes: comentarios,
        }
      }

      queryClient.setQueriesData<{ content: Postagem[] }>(
        { queryKey: ['feed-postagens'] },
        (oldData) => {
          if (!oldData || !oldData.content) return oldData
          return {
            ...oldData,
            content: oldData.content.map(remover),
          }
        },
      )

      queryClient.setQueriesData<Postagem[]>(
        { queryKey: ['mural-avisos'] },
        (oldData) => (oldData ? oldData.map(remover) : oldData),
      )
    },
  })
}
