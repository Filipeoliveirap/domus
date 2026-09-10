import { useQuery, useQueryClient } from '@tanstack/react-query'
import { ministerioService } from '@/services/ministerio.service'
import type { MinisterioResponse, MinisterioDetalheResponse } from '@/types/ministerio.type'

export function useMinisterioDetalhe(id: string) {
  const queryClient = useQueryClient()
  return useQuery({
    queryKey: ['ministerios', id],
    queryFn: () => ministerioService.detalhe(id),
    enabled: !!id,
    // Semeia o detalhe com o que a LISTA já tem em cache (nome, foto) para o cabeçalho
    // renderizar na hora — sem <Skeleton>. É o que deixa a navegação imersiva (a cópia
    // do card crescendo, ver useNavegacaoImersiva) revelar a tela já montada por baixo.
    // `initialDataUpdatedAt: 0` marca como velho na hora → refetch imediato pro dado cheio.
    initialData: () => {
      const lista = queryClient.getQueryData<MinisterioResponse[]>(['ministerios'])
      const item = lista?.find((m) => m.id === id)
      if (!item) return undefined
      return {
        id: item.id,
        nome: item.nome,
        fotoId: item.fotoId,
        souLiderDesteMinisterio: item.souLiderDesteMinisterio,
        membros: [],
        pedidosPendentes: [],
        souMembroAtivo: false,
        tenhoPedidoPendente: false,
        arquivada: false,
      } satisfies MinisterioDetalheResponse
    },
    initialDataUpdatedAt: 0,
  })
}
