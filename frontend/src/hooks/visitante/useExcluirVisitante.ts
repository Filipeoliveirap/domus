import { useState } from 'react'
import axios from 'axios'
import { notificar } from '@/components/common/Notificacao/notificar'
import { useQueryClient } from '@tanstack/react-query'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { visitanteService } from '@/services/visitante.service'
import type { VisitanteResponse } from '@/types/visitante.type'
import type { ApiError } from '@/types/api.types'

export function useExcluirVisitante(visitante: VisitanteResponse, onClose: () => void) {
  const queryClient = useQueryClient()
  const [erroGeral, setErroGeral] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(false)

  const confirmar = async () => {
    setErroGeral(null)
    setIsLoading(true)
    try {
      await visitanteService.excluir(visitante.id)
      invalidarCache(queryClient, 'visitante')
      notificar.sucesso(`${visitante.nome} foi excluído.`)
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
