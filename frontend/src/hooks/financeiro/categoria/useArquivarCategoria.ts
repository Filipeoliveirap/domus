import { useState } from 'react'
import axios from 'axios'
import { toast } from 'sonner'
import { useQueryClient } from '@tanstack/react-query'
import { categoriasService } from '@/services/financeiro/categoria.service'
import type { CategoriaResponse } from '@/types/financeiro/categoria.type'
import type { ApiError } from '@/types/api.types'

export function useArquivarCategoria(categoria: CategoriaResponse, onClose: () => void) {
  const queryClient = useQueryClient()
  const [erroGeral, setErroGeral] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(false)

  const confirmar = async () => {
    setErroGeral(null)
    setIsLoading(true)
    try {
      await categoriasService.arquivar(categoria.id)
      queryClient.invalidateQueries({ queryKey: ['categorias'] })
      toast.success(`"${categoria.nome}" foi arquivada.`)
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