import { useState } from 'react'
import axios from 'axios'
import { notificar } from '@/components/common/Notificacao/notificar'
import { useQueryClient } from '@tanstack/react-query'
import { movimentacoesService } from '@/services/financeiro/movimentacao.service'
import type { MovimentacaoResponse } from '@/types/financeiro/movimentacao.type'
import type { ApiError } from '@/types/api.types'

export function useArquivarMovimentacao(movimentacao: MovimentacaoResponse, onClose: () => void) {
  const queryClient = useQueryClient()
  const [erroGeral, setErroGeral] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(false)

  const confirmar = async () => {
    setErroGeral(null)
    setIsLoading(true)
    try {
      await movimentacoesService.arquivar(movimentacao.id)
      queryClient.invalidateQueries({ queryKey: ['movimentacoes'] })
      queryClient.invalidateQueries({ queryKey: ['relatorios'] })  
      notificar.sucesso('Movimentação arquivada.')
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