import { useState } from 'react'
import axios from 'axios'
import { useQueryClient } from '@tanstack/react-query'
import { notificar } from '@/components/common/Notificacao/notificar'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { celulaService } from '@/services/celula.service'
import type { CelulaResponse } from '@/types/celula.type'
import type { ApiError } from '@/types/api.types'

export function useExcluirCelulaDefinitivamente(celula: CelulaResponse, onClose: () => void) {
  const queryClient = useQueryClient()
  const [erroGeral, setErroGeral] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(false)

  const confirmar = async () => {
    setErroGeral(null)
    setIsLoading(true)
    try {
      await celulaService.excluirDefinitivo(celula.id)
      invalidarCache(queryClient, 'celula')
      notificar.sucesso(`${celula.nome} foi excluída definitivamente.`)
      onClose()
    } catch (error: unknown) {
      if (axios.isAxiosError<ApiError>(error)) {
        setErroGeral(error.response?.data?.message ?? 'Erro ao excluir. Tente novamente.')
      } else {
        setErroGeral('Erro ao excluir. Tente novamente.')
      }
    } finally {
      setIsLoading(false)
    }
  }

  return { confirmar, isLoading, erroGeral }
}
