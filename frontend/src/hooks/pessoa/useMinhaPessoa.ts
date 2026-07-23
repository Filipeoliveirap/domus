import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { pessoasService } from '@/services/pessoa.service'
import { useAuthStore } from '@/store/authStore'
import type { PessoaRequest } from '@/types/pessoa.type'

export function useMinhaPessoa() {
  return useQuery({
    queryKey: ['pessoa', 'me'],
    queryFn: () => pessoasService.buscarMe(),
  })
}

export function useAtualizarMinhaPessoa() {
  const queryClient = useQueryClient()
  const atualizarUsuarioLogado = useAuthStore((s) => s.atualizarUsuarioLogado)

  return useMutation({
    mutationFn: (data: PessoaRequest) => pessoasService.atualizarMe(data),
    onSuccess: (resposta) => {
      queryClient.setQueryData(['pessoa', 'me'], resposta)
      // Sidebar e authStore usam nome/fotoId da sessão — sem isto, a troca de foto
      // só apareceria lá depois de um F5.
      atualizarUsuarioLogado({ nome: resposta.nome, fotoId: resposta.fotoId })
    },
  })
}
