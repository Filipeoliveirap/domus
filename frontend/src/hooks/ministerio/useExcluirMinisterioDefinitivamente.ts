import { useState } from 'react'
import axios from 'axios'
import { useQueryClient } from '@tanstack/react-query'
import { notificar } from '@/components/common/Notificacao/notificar'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { ministerioService } from '@/services/ministerio.service'
import type { MinisterioResponse } from '@/types/ministerio.type'
import type { ApiError } from '@/types/api.types'

export function useExcluirMinisterioDefinitivamente(ministerio: MinisterioResponse, onClose: () => void) {
  const queryClient = useQueryClient()
  const [erroGeral, setErroGeral] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(false)

  const confirmar = async () => {
    setErroGeral(null)
    setIsLoading(true)
    try {
      await ministerioService.excluirDefinitivo(ministerio.id)
      invalidarCache(queryClient, 'ministerio')
      notificar.sucesso(`${ministerio.nome} foi excluída definitivamente.`)
      onClose()
    } catch (error: unknown) {
      const mensagem = axios.isAxiosError<ApiError>(error)
        ? error.response?.data?.message ?? 'Erro ao excluir. Tente novamente.'
        : 'Erro ao excluir. Tente novamente.'
      setErroGeral(mensagem)
      notificar.erro(mensagem)
    } finally {
      setIsLoading(false)
    }
  }

  return { confirmar, isLoading, erroGeral }
}
