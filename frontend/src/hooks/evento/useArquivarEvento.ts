import { useState } from 'react'
import axios from 'axios'
import { toast } from 'sonner'
import { useQueryClient } from '@tanstack/react-query'
import { eventosService } from '@/services/evento.service'
import type { EventoResponse } from '@/types/evento.type'
import type { ApiError } from '@/types/api.types'

export function useArquivarEvento(evento: EventoResponse, onClose: () => void) {
  const queryClient = useQueryClient()
  const [erroGeral, setErroGeral] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(false)

  const confirmar = async () => {
    setErroGeral(null)
    setIsLoading(true)
    try {
      await eventosService.arquivar(evento.id)
      queryClient.invalidateQueries({ queryKey: ['eventos'] })
      toast.success(`"${evento.titulo}" foi arquivado.`)
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