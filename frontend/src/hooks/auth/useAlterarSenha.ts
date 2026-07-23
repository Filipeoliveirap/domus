import { useMutation } from '@tanstack/react-query'
import { authService } from '@/services/auth.service'
import type { AlterarSenhaRequest } from '@/types/auth.types'

export function useAlterarSenha() {
  return useMutation({
    mutationFn: (data: AlterarSenhaRequest) => authService.alterarSenha(data),
  })
}
