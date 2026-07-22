import { useQuery } from '@tanstack/react-query'
import { pessoasService } from '@/services/pessoa.service'

export function usePessoa(id: string | undefined) {
  return useQuery({
    queryKey: ['pessoa', id],
    queryFn: () => pessoasService.buscar(id!),
    enabled: !!id,
  })
}
