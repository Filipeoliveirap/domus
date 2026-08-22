import { useState } from 'react'
import axios from 'axios'
import { useQueryClient } from '@tanstack/react-query'
import { notificar } from '@/components/common/Notificacao/notificar'
import { inscricoesService } from '@/services/inscricao.service'
import type { RespostaRequest } from '@/types/campoPersonalizado.type'
import type { ApiError } from '@/types/api.types'

export function useResponderCampos() {
  const queryClient = useQueryClient()
  const [isLoading, setIsLoading] = useState(false)
  const [erro, setErro] = useState<string | null>(null)

  async function responder(inscricaoId: string, dados: RespostaRequest[], acompanhanteId?: string) {
    setIsLoading(true)
    setErro(null)
    try {
      await inscricoesService.responder(inscricaoId, dados, acompanhanteId)
      queryClient.invalidateQueries({ queryKey: ['respostas-campos', inscricaoId, acompanhanteId ?? null] })
      notificar.sucesso('Respostas salvas.')
      return true
    } catch (error: unknown) {
      const mensagem = axios.isAxiosError<ApiError>(error)
        ? error.response?.data?.message ?? 'Erro ao salvar. Tente novamente.'
        : 'Erro ao salvar. Tente novamente.'
      setErro(mensagem)
      notificar.erro(mensagem)
      return false
    } finally {
      setIsLoading(false)
    }
  }

  return { responder, isLoading, erro }
}
