import { useQuery } from '@tanstack/react-query'
import { locaisEventoService } from '@/services/localEvento.service'

// Locais cadastrados pela igreja, para o <SeletorLocal>. Qualquer usuário autenticado
// pode listar (não é uma tela de gestão, é só o que alimenta o select do formulário).
export function useLocaisEvento() {
  return useQuery({
    queryKey: ['eventos', 'locais'],
    queryFn: () => locaisEventoService.listar(),
    staleTime: 5 * 60 * 1000,
  })
}
