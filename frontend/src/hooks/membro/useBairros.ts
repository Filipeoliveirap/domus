import { useQuery } from '@tanstack/react-query'
import { membrosService } from '@/services/membro.service'

// Bairros já cadastrados na igreja, para o <datalist> do form (camada 2 da
// padronização: sugere o que já existe em vez de deixar cada um digitar variações).
export function useBairros() {
  return useQuery({
    queryKey: ['membros', 'bairros'],
    queryFn: () => membrosService.listarBairros(),
    staleTime: 5 * 60 * 1000,
  })
}
