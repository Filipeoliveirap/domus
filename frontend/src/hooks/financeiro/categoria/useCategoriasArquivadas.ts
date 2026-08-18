import { useQuery } from '@tanstack/react-query'
import { categoriasService } from '@/services/financeiro/categoria.service'

export function useCategoriasArquivadas() {
  return useQuery({
    queryKey: ['categorias-arquivadas'],
    queryFn: () => categoriasService.listarArquivadas(),
  })
}
