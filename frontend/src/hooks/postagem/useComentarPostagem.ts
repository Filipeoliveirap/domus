import { useMutation, useQueryClient } from '@tanstack/react-query'
import { postagemService } from '@/services/postagem.service'
import type { Postagem, Comentario } from '@/types/postagem.type'

export function useComentarPostagem() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({
      postagemId,
      conteudo,
      paiComentarioId,
    }: {
      postagemId: string
      conteudo: string
      paiComentarioId?: string | null
    }) => postagemService.comentar(postagemId, conteudo, paiComentarioId),

    onSuccess: (novoComentario: Comentario, { postagemId }) => {
      queryClient.setQueriesData<{ content: Postagem[]; totalPages?: number; totalElements?: number }>(
        { queryKey: ['feed-postagens'] },
        (oldData) => {
          if (!oldData || !oldData.content) return oldData
          return {
            ...oldData,
            content: oldData.content.map((post) => {
              if (post.id !== postagemId) return post
              const comentariosAtuais = post.comentariosRecentes ?? []
              const jaExiste = comentariosAtuais.some((c) => c.id === novoComentario.id)
              const novosComentarios = jaExiste ? comentariosAtuais : [...comentariosAtuais, novoComentario]
              return {
                ...post,
                totalComentarios: post.totalComentarios + 1,
                comentariosRecentes: novosComentarios,
              }
            }),
          }
        },
      )

      queryClient.setQueriesData<Postagem[]>(
        { queryKey: ['mural-avisos'] },
        (oldData) => {
          if (!oldData) return oldData
          return oldData.map((post) => {
            if (post.id !== postagemId) return post
            const comentariosAtuais = post.comentariosRecentes ?? []
            const jaExiste = comentariosAtuais.some((c) => c.id === novoComentario.id)
            const novosComentarios = jaExiste ? comentariosAtuais : [...comentariosAtuais, novoComentario]
            return {
              ...post,
              totalComentarios: post.totalComentarios + 1,
              comentariosRecentes: novosComentarios,
            }
          })
        },
      )
    },
  })
}
