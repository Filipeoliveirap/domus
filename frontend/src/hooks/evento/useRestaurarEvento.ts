import { useState } from 'react'
import axios from 'axios'
import { useQueryClient } from '@tanstack/react-query'
import { notificar } from '@/components/common/Notificacao/notificar'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { eventosService } from '@/services/evento.service'
import type { ApiError } from '@/types/api.types'
import type { EscopoEdicaoEvento } from '@/types/evento.type'

export function useRestaurarEvento() {
  const queryClient = useQueryClient()
  const [isLoading, setIsLoading] = useState(false)

  async function restaurar(id: string, titulo: string, escopo?: EscopoEdicaoEvento) {
    setIsLoading(true)
    try {
      await eventosService.restaurar(id, escopo)
      invalidarCache(queryClient, 'evento')
      notificar.sucesso(`"${titulo}" foi restaurado.`)
    } catch (error: unknown) {
      const mensagem = axios.isAxiosError<ApiError>(error)
        ? error.response?.data?.message ?? 'Erro ao restaurar. Tente novamente.'
        : 'Erro ao restaurar. Tente novamente.'
      notificar.erro(mensagem)
    } finally {
      setIsLoading(false)
    }
  }

  return { restaurar, isLoading }
}
