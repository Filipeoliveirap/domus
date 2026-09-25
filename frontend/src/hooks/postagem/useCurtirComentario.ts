import { useMutation, useQueryClient } from '@tanstack/react-query'
import { postagemService } from '@/services/postagem.service'
import type { Postagem } from '@/types/postagem.type'

export function useCurtirComentario() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ postagemId, comentarioId }: { postagemId: string; comentarioId: string }) =>
      postagemService.curtirComentario(comentarioId),

    onMutate: async ({ postagemId, comentarioId }) => {
      await queryClient.cancelQueries({ queryKey: ['feed-postagens'] })
      await queryClient.cancelQueries({ queryKey: ['mural-avisos'] })

      const feedQueries = queryClient.getQueriesData<{ content: Postagem[] }>({ queryKey: ['feed-postagens'] })
      const muralQueries = queryClient.getQueriesData<Postagem[]>({ queryKey: ['mural-avisos'] })

      const atualizarComentarioEmPost = (post: Postagem): Postagem => {
        if (post.id !== postagemId) return post
        const comentarios = (post.comentariosRecentes ?? []).map((c) => {
          if (c.id !== comentarioId) return c
          const jaCurtiu = c.curtidoPorMim
          const diff = jaCurtiu ? -1 : 1
          return {
            ...c,
            curtidoPorMim: !jaCurtiu,
            totalCurtidas: Math.max(0, c.totalCurtidas + diff),
          }
        })
        return {
          ...post,
          comentariosRecentes: comentarios,
        }
      }

      queryClient.setQueriesData<{ content: Postagem[] }>(
        { queryKey: ['feed-postagens'] },
        (oldData) => {
          if (!oldData || !oldData.content) return oldData
          return {
            ...oldData,
            content: oldData.content.map(atualizarComentarioEmPost),
          }
        },
      )

      queryClient.setQueriesData<Postagem[]>(
        { queryKey: ['mural-avisos'] },
        (oldData) => {
          if (!oldData) return oldData
          return oldData.map(atualizarComentarioEmPost)
        },
      )

      return { feedQueries, muralQueries }
    },

    onError: (_err, _variables, context) => {
      if (context?.feedQueries) {
        for (const [queryKey, data] of context.feedQueries) {
          queryClient.setQueryData(queryKey, data)
        }
      }
      if (context?.muralQueries) {
        for (const [queryKey, data] of context.muralQueries) {
          queryClient.setQueryData(queryKey, data)
        }
      }
    },

    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ['feed-postagens'] })
      queryClient.invalidateQueries({ queryKey: ['mural-avisos'] })
    },
  })
}
