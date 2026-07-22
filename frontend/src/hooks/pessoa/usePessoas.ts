// hooks/pessoa/usePessoa.ts
import { useQuery, keepPreviousData } from '@tanstack/react-query'
import { pessoasService } from '@/services/pessoa.service'


interface UsePessoasParams {
  q: string
  page: number
  size?: number
}
export function usePessoas({ q, page, size = 20 }: UsePessoasParams) {
  return useQuery({
    queryKey: ['pessoas', { q, page, size }],
    queryFn: () => pessoasService.listar({ q, page, size }),
    placeholderData: keepPreviousData,
  })
}
