import { useState } from 'react'
import axios from 'axios'
import { notificar } from '@/components/common/Notificacao/notificar'
import { useQueryClient } from '@tanstack/react-query'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { membrosService } from '@/services/membro.service'
import type { MembroResponse } from '@/types/membro.type'
import type { ApiError } from '@/types/api.types'

export function useArquivarMembro(membro: MembroResponse, onClose: () => void) {
  const queryClient = useQueryClient()
  const [erroGeral, setErroGeral] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(false)

  const confirmar = async () => {
    setErroGeral(null)
    setIsLoading(true)
    try {
      await membrosService.arquivar(membro.id)
      invalidarCache(queryClient, 'membro')
      notificar.sucesso(`${membro.nome} foi arquivado.`)
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