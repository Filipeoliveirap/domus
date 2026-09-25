import { useMutation, useQueryClient } from '@tanstack/react-query'
import { postagemService } from '@/services/postagem.service'
import type { Postagem, Comentario } from '@/types/postagem.type'

export function useAtualizarComentario() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({
      postagemId,
      comentarioId,
      conteudo,
    }: {
      postagemId: string
      comentarioId: string
      conteudo: string
    }) => postagemService.atualizarComentario(postagemId, comentarioId, conteudo),

    onSuccess: (comentarioAtualizado: Comentario, { postagemId }) => {
      const atualizar = (post: Postagem): Postagem => {
        if (post.id !== postagemId) return post
        const comentarios = (post.comentariosRecentes ?? []).map((c) =>
          c.id === comentarioAtualizado.id ? comentarioAtualizado : c,
        )
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
            content: oldData.content.map(atualizar),
          }
        },
      )

      queryClient.setQueriesData<Postagem[]>(
        { queryKey: ['mural-avisos'] },
        (oldData) => (oldData ? oldData.map(atualizar) : oldData),
      )
    },
  })
}
