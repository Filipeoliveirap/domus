import { useMutation, useQueryClient } from '@tanstack/react-query'
import { postagemService, type CriarPostagemPayload } from '@/services/postagem.service'
import type { Postagem } from '@/types/postagem.type'

export function useAtualizarPostagem() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ postagemId, payload }: { postagemId: string; payload: Partial<CriarPostagemPayload> }) =>
      postagemService.atualizar(postagemId, payload),
    onSuccess: (postAtualizada) => {
      queryClient.setQueriesData<{ content: Postagem[] }>(
        { queryKey: ['feed-postagens'] },
        (oldData) => {
          if (!oldData || !oldData.content) return oldData
          return {
            ...oldData,
            content: oldData.content.map((p) => (p.id === postAtualizada.id ? { ...p, ...postAtualizada } : p)),
          }
        },
      )

      queryClient.setQueriesData<Postagem[]>(
        { queryKey: ['mural-avisos'] },
        (oldData) => (oldData ? oldData.map((p) => (p.id === postAtualizada.id ? { ...p, ...postAtualizada } : p)) : oldData),
      )

      queryClient.invalidateQueries({ queryKey: ['feed-postagens'] })
      queryClient.invalidateQueries({ queryKey: ['mural-avisos'] })
    },
  })
}
