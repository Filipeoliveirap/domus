import { useMutation, useQueryClient } from '@tanstack/react-query'
import { postagemService } from '@/services/postagem.service'
import type { Postagem, TipoReacao } from '@/types/postagem.type'

export function useCurtirPostagem() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ postagemId, tipo }: { postagemId: string; tipo: TipoReacao }) =>
      postagemService.curtir(postagemId, tipo),

    onMutate: async ({ postagemId, tipo }) => {
      await queryClient.cancelQueries({ queryKey: ['feed-postagens'] })
      await queryClient.cancelQueries({ queryKey: ['mural-avisos'] })

      const feedQueries = queryClient.getQueriesData<{ content: Postagem[] }>({ queryKey: ['feed-postagens'] })
      const muralQueries = queryClient.getQueriesData<Postagem[]>({ queryKey: ['mural-avisos'] })

      const atualizarPost = (post: Postagem): Postagem => {
        if (post.id !== postagemId) return post
        const jaCurtiu = post.minhaReacao === tipo
        const novaReacao = jaCurtiu ? null : tipo
        const diff = jaCurtiu ? -1 : 1
        return {
          ...post,
          minhaReacao: novaReacao,
          totalCurtidas: Math.max(0, post.totalCurtidas + diff),
        }
      }

      queryClient.setQueriesData<{ content: Postagem[]; totalPages?: number; totalElements?: number }>(
        { queryKey: ['feed-postagens'] },
        (oldData) => {
          if (!oldData || !oldData.content) return oldData
          return {
            ...oldData,
            content: oldData.content.map(atualizarPost),
          }
        },
      )

      queryClient.setQueriesData<Postagem[]>(
        { queryKey: ['mural-avisos'] },
        (oldData) => {
          if (!oldData) return oldData
          return oldData.map(atualizarPost)
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
