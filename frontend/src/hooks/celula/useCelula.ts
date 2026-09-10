import { useQuery, useQueryClient } from '@tanstack/react-query'
import { celulaService } from '@/services/celula.service'
import type { CelulaResponse, CelulaDetalheResponse } from '@/types/celula.type'

export function useCelula(id: string | undefined) {
  const queryClient = useQueryClient()
  return useQuery({
    queryKey: ['celulas', id],
    queryFn: () => celulaService.buscar(id!),
    enabled: !!id,
    // Semeia o detalhe com o que a LISTA já tem em cache (nome, foto, dia/horário) para
    // o cabeçalho renderizar na hora — sem passar por <Skeleton>. É o que faz a transição
    // de "entrar no card" ter um alvo pra onde a foto/título do card se transformar
    // (ver useNavegacaoImersiva). `membros: []` fica só até o fetch completo chegar.
    // `initialDataUpdatedAt: 0` marca como velho na hora → refetch imediato pro dado cheio.
    initialData: () => {
      const lista = queryClient.getQueryData<CelulaResponse[]>(['celulas'])
      const item = lista?.find((c) => c.id === id)
      if (!item) return undefined
      return {
        id: item.id,
        nome: item.nome,
        fotoId: item.fotoId,
        diaSemana: item.diaSemana,
        horario: item.horario,
        souLiderDestaCelula: item.souLiderDestaCelula,
        membros: [],
        arquivada: false,
      } satisfies CelulaDetalheResponse
    },
    initialDataUpdatedAt: 0,
  })
}
