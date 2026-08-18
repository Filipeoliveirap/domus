import { useQuery } from '@tanstack/react-query'
import { pessoasService } from '@/services/pessoa.service'

export function usePessoasArquivadas() {
  return useQuery({
    queryKey: ['pessoas-arquivadas'],
    queryFn: () => pessoasService.listarArquivadas(),
  })
}
