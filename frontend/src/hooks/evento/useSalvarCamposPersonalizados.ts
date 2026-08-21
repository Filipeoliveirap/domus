import { useState } from 'react'
import axios from 'axios'
import { useQueryClient } from '@tanstack/react-query'
import { notificar } from '@/components/common/Notificacao/notificar'
import { camposPersonalizadosService } from '@/services/campoPersonalizado.service'
import type { CampoPersonalizadoRequest } from '@/types/campoPersonalizado.type'
import type { ApiError } from '@/types/api.types'

export function useSalvarCamposPersonalizados() {
  const queryClient = useQueryClient()
  const [isLoading, setIsLoading] = useState(false)

  async function salvar(eventoId: string, dados: CampoPersonalizadoRequest[]) {
    setIsLoading(true)
    try {
      await camposPersonalizadosService.salvar(eventoId, dados)
      queryClient.invalidateQueries({ queryKey: ['campos-personalizados', eventoId] })
      notificar.sucesso('Campos personalizados salvos.')
    } catch (error: unknown) {
      const mensagem = axios.isAxiosError<ApiError>(error)
        ? error.response?.data?.message ?? 'Erro ao salvar. Tente novamente.'
        : 'Erro ao salvar. Tente novamente.'
      notificar.erro(mensagem)
      throw error
    } finally {
      setIsLoading(false)
    }
  }

  return { salvar, isLoading }
}
