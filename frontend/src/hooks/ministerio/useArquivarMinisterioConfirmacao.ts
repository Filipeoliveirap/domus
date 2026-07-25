import { useState } from 'react'
import axios from 'axios'
import { useQueryClient } from '@tanstack/react-query'
import { notificar } from '@/components/common/Notificacao/notificar'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { ministerioService } from '@/services/ministerio.service'
import type { MinisterioResponse } from '@/types/ministerio.type'
import type { ApiError } from '@/types/api.types'

// Reaproveita ministerioService.arquivar diretamente (não a mutation useArquivarMinisterio
// do Task 9) porque este fluxo tem seu próprio notificar.sucesso com o nome do ministério —
// espelha exatamente useArquivarLocalEvento.ts.
export function useArquivarMinisterioConfirmacao(ministerio: MinisterioResponse, onClose: () => void) {
  const queryClient = useQueryClient()
  const [erroGeral, setErroGeral] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(false)

  const confirmar = async () => {
    setErroGeral(null)
    setIsLoading(true)
    try {
      await ministerioService.arquivar(ministerio.id)
      invalidarCache(queryClient, 'ministerio')
      notificar.sucesso(`"${ministerio.nome}" foi arquivado.`)
      onClose()
    } catch (error: unknown) {
      if (axios.isAxiosError<ApiError>(error)) {
        setErroGeral(error.response?.data?.message ?? 'Erro ao arquivar. Tente novamente.')
      } else {
        setErroGeral('Erro ao arquivar. Tente novamente.')
      }
    } finally {
      setIsLoading(false)
    }
  }

  return { confirmar, isLoading, erroGeral }
}
