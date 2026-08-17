import { useQuery } from '@tanstack/react-query'
import { useDebounce } from '@/hooks/useDebounce'
import { api } from '@/lib/api'

export interface ResultadoBusca {
  id: string
  tipo: 'PESSOA' | 'EVENTO' | 'USUARIO' | 'MOVIMENTACAO' | 'CATEGORIA' | 'CELULA' | 'VISITANTE' | 'MINISTERIO'
  titulo: string
  subtitulo: string
  // Só vem preenchido pra VISITANTE que está numa célula (não aparece mais na
  // listagem de visitantes) — nesse caso a rota é a célula, não a lista.
  celulaId?: string | null
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