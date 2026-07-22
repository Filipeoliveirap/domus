// hooks/pessoa/usePessoa.ts
import { useQuery, keepPreviousData } from '@tanstack/react-query'
import { pessoasService } from '@/services/pessoa.service'
import type { Vinculo } from '@/types/pessoa.type'


interface UsePessoasParams {
  q: string
  page: number
  size?: number
  /** '' (ou ausente) = sem filtro. Entra na queryKey: senão o TanStack devolveria do
   * cache a lista de um vínculo diferente do que foi pedido agora. */
  vinculo?: Vinculo | ''
}
export function usePessoas({ q, page, size = 20, vinculo = '' }: UsePessoasParams) {
  return useQuery({
    queryKey: ['pessoas', { q, page, size, vinculo }],
    queryFn: () => pessoasService.listar({ q, page, size, vinculo }),
    placeholderData: keepPreviousData,
  })
}
