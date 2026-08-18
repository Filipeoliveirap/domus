import { useState } from 'react'
import axios from 'axios'
import { useQueryClient } from '@tanstack/react-query'
import { notificar } from '@/components/common/Notificacao/notificar'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { categoriasService } from '@/services/financeiro/categoria.service'
import type { ApiError } from '@/types/api.types'

export function useRestaurarCategoria() {
  const queryClient = useQueryClient()
  const [isLoading, setIsLoading] = useState(false)

  async function restaurar(id: string, nome: string) {
    setIsLoading(true)
    try {
      await categoriasService.restaurar(id)
      invalidarCache(queryClient, 'categoria')
      notificar.sucesso(`"${nome}" foi restaurada.`)
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
