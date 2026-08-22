import { useMutation } from '@tanstack/react-query'
import { conviteService } from '@/services/convite.service'
import type { EntrarConviteRequest } from '@/types/convite.type'

export function useEntrarComoConvidado(token: string) {
  return useMutation({
    mutationFn: (dados: EntrarConviteRequest) => conviteService.entrar(token, dados),
  })
}
