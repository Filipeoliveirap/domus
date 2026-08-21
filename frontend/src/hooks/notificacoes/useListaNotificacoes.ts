import { useQuery } from '@tanstack/react-query'
import { notificacaoCentralService } from '@/services/notificacaoCentral.service'

// Só busca a lista quando o dropdown está aberto — não mantém sincronizada em segundo plano.
export function useListaNotificacoes(habilitado: boolean) {
  return useQuery({
    queryKey: ['notificacoes', 'lista'],
    queryFn: () => notificacaoCentralService.listar(),
    enabled: habilitado,
  })
}
