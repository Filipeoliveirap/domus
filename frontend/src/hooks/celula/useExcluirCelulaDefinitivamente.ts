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
      const mensagem = axios.isAxiosError<ApiError>(error)
        ? error.response?.data?.message ?? 'Erro ao excluir. Tente novamente.'
        : 'Erro ao excluir. Tente novamente.'
      // erroGeral é exibido inline pelo ModalConfirmacaoCritica; o toast garante que o
      // erro também aparece quando quem chama é o ModalConfirmacao simples (sem esse slot).
      setErroGeral(mensagem)
      notificar.erro(mensagem)
    } finally {
      setIsLoading(false)
    }
  }

  return { confirmar, isLoading, erroGeral }
}
