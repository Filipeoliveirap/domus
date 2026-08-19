import { useState } from 'react'
import axios from 'axios'
import { useQueryClient } from '@tanstack/react-query'
import { notificar } from '@/components/common/Notificacao/notificar'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { pessoasService } from '@/services/pessoa.service'
import type { ApiError } from '@/types/api.types'

export function useRestaurarPessoa() {
  const queryClient = useQueryClient()
  const [isLoading, setIsLoading] = useState(false)

  async function restaurar(id: string, nome: string) {
    setIsLoading(true)
    try {
      await pessoasService.restaurar(id)
      invalidarCache(queryClient, 'pessoa')
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
