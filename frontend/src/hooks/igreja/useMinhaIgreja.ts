import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { Endpoints } from '@/lib/endpoints'
import { notificar } from '@/components/common/Notificacao/notificar'
import { useAuthStore } from '@/store/authStore'
import type { AtualizarIgrejaRequest, IgrejaDetalhe } from '@/types/igreja/igreja.type'

const CHAVE = ['igreja', 'minha']

export function useMinhaIgreja(enabled = true) {
  return useQuery({
    queryKey: CHAVE,
    queryFn: async (): Promise<IgrejaDetalhe> => {
      const { data } = await api.get(Endpoints.igreja.MINHA)
      return data
    },
    enabled,
  })
}

/**
 * Salva só a logo — dispara assim que o UploadFoto confirma o recorte (ou remove a
 * foto), sem esperar o resto do formulário de Dados da Igreja ser salvo.
 */
export function useAtualizarLogoIgreja() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async (fotoId: string | null): Promise<void> => {
      await api.patch(Endpoints.igreja.LOGO, { fotoId })
    },
    onSuccess: (_, fotoId) => {
      queryClient.setQueryData<IgrejaDetalhe | undefined>(CHAVE, (atual) =>
        atual ? { ...atual, logoFotoId: fotoId } : atual,
      )
      useAuthStore.getState().atualizarUsuarioLogado({ igrejaLogoId: fotoId })
    },
    onError: (erro: unknown) => {
      const mensagem =
        (erro as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'Tente novamente em alguns instantes.'
      notificar.erro('Não foi possível salvar a foto', mensagem)
    },
  })
}

export function useAtualizarIgreja() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async (body: AtualizarIgrejaRequest): Promise<IgrejaDetalhe> => {
      const { data } = await api.put(Endpoints.igreja.MINHA, body)
      return data
    },
    onSuccess: (igreja) => {
      // A resposta já traz o estado novo (inclusive a auditoria) — evita um GET extra.
      queryClient.setQueryData(CHAVE, igreja)
      useAuthStore.getState().atualizarUsuarioLogado({
        igrejaSigla: igreja.sigla || null,
        igrejaLogoId: igreja.logoFotoId || null,
      })
      notificar.sucesso('Configurações salvas', 'As informações da instituição foram atualizadas.')
    },
    onError: (erro: unknown) => {
      const mensagem =
        (erro as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'Tente novamente em alguns instantes.'
      notificar.erro('Não foi possível salvar', mensagem)
    },
  })
}
