import { useQuery } from '@tanstack/react-query'
import { useDebounce } from '@/hooks/useDebounce'
import { api } from '@/lib/api'

export interface ResultadoBusca {
  id: string
  tipo: 'MEMBRO' | 'EVENTO' | 'USUARIO' | 'MOVIMENTACAO' | 'CATEGORIA'
  titulo: string
  subtitulo: string
}

export function useBuscaGlobal(termo: string) {
  const termoDebounced = useDebounce(termo, 250)

  return useQuery({
    queryKey: ['busca-global', termoDebounced],
    queryFn: async () => {
      const { data } = await api.get<ResultadoBusca[]>('/busca/global', {
        params: { q: termoDebounced },
      })
      return data
    },
    enabled: termoDebounced.trim().length >= 2,
    staleTime: 0,        
    gcTime: 1000 * 30,  
  })
}