import { useQuery } from '@tanstack/react-query'
import { cobrancaService } from '@/services/cobranca.service'

/** Página pública `/cobranca/[token]` — sem sessão, por isso `retry: false` (um token
 *  inválido/expirado é resposta legítima, não falha transitória de rede). */
export function useCobrancaPublica(token: string) {
  return useQuery({
    queryKey: ['cobranca-publica', token],
    queryFn: () => cobrancaService.buscarPorToken(token),
    retry: false,
  })
}
