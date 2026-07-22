import { useQuery } from '@tanstack/react-query'
import { pessoasService } from '@/services/pessoa.service'

// Bairros já cadastrados na igreja, para o <datalist> do form (camada 2 da
// padronização: sugere o que já existe em vez de deixar cada um digitar variações).
export function useBairros() {
  return useQuery({
    queryKey: ['pessoas', 'bairros'],
    queryFn: () => pessoasService.listarBairros(),
    staleTime: 5 * 60 * 1000,
  })
}
