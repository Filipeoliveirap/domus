import { useState } from 'react'
import axios from 'axios'
import { useQueryClient } from '@tanstack/react-query'
import { notificar } from '@/components/common/Notificacao/notificar'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { pessoasService } from '@/services/pessoa.service'
import type { PessoaArquivadaResponse } from '@/types/pessoa.type'
import type { ApiError } from '@/types/api.types'

export function useExcluirPessoaDefinitivamente(pessoa: PessoaArquivadaResponse, onClose: () => void) {
  const queryClient = useQueryClient()
  const [erroGeral, setErroGeral] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(false)

  const confirmar = async () => {
    setErroGeral(null)
    setIsLoading(true)
    try {
      await pessoasService.excluirDefinitivo(pessoa.id)
      invalidarCache(queryClient, 'pessoa')
      notificar.sucesso(`"${pessoa.nome}" foi excluída definitivamente.`)
      onClose()
    } catch (error: unknown) {
      const mensagem = axios.isAxiosError<ApiError>(error)
        ? error.response?.data?.message ?? 'Erro ao excluir. Tente novamente.'
        : 'Erro ao excluir. Tente novamente.'
      setErroGeral(mensagem)
      notificar.erro(mensagem)
    } finally {
      setIsLoading(false)
    }
  }

  return { confirmar, isLoading, erroGeral }
}
