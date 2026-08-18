import { useState } from 'react'
import axios from 'axios'
import { useQueryClient } from '@tanstack/react-query'
import { notificar } from '@/components/common/Notificacao/notificar'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { locaisEventoService } from '@/services/localEvento.service'
import type { LocalEventoResponse } from '@/types/evento.type'
import type { ApiError } from '@/types/api.types'

export function useArquivarLocalEvento(local: LocalEventoResponse, onClose: () => void) {
  const queryClient = useQueryClient()
  const [erroGeral, setErroGeral] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(false)

  const confirmar = async () => {
    setErroGeral(null)
    setIsLoading(true)
    try {
      await locaisEventoService.arquivar(local.id)
      invalidarCache(queryClient, 'localEvento')
      notificar.sucesso(`"${local.nome}" foi arquivado.`)
      onClose()
    } catch (error: unknown) {
      const mensagem = axios.isAxiosError<ApiError>(error)
        ? error.response?.data?.message ?? 'Erro ao arquivar. Tente novamente.'
        : 'Erro ao arquivar. Tente novamente.'
      setErroGeral(mensagem)
      notificar.erro(mensagem)
    } finally {
      setIsLoading(false)
    }
  }

  return { confirmar, isLoading, erroGeral }
}
