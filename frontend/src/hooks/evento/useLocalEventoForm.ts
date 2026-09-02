import { useState } from 'react'
import axios from 'axios'
import { useQueryClient } from '@tanstack/react-query'
import { notificar } from '@/components/common/Notificacao/notificar'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { locaisEventoService } from '@/services/localEvento.service'
import type { LocalEventoRequest, LocalEventoResponse } from '@/types/evento.type'
import type { ApiError } from '@/types/api.types'

export function useLocalEventoForm(
  local: LocalEventoResponse | null,
  onClose: () => void,
  onCriado?: (local: LocalEventoResponse) => void,
) {
  const queryClient = useQueryClient()
  const [erroGeral, setErroGeral] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(false)

  const salvar = async (data: LocalEventoRequest) => {
    setErroGeral(null)
    setIsLoading(true)
    try {
      if (local) {
        await locaisEventoService.atualizar(local.id, data)
        notificar.sucesso(`"${data.nome}" foi atualizado.`)
      } else {
        const criado = await locaisEventoService.criar(data)
        notificar.sucesso(`"${data.nome}" foi cadastrado.`)
        onCriado?.(criado)
      }
      invalidarCache(queryClient, 'localEvento')
      onClose()
    } catch (error: unknown) {
      if (axios.isAxiosError<ApiError>(error)) {
        setErroGeral(error.response?.data?.message ?? 'Não foi possível salvar o local. Tente novamente.')
      } else {
        setErroGeral('Não foi possível salvar o local. Tente novamente.')
      }
    } finally {
      setIsLoading(false)
    }
  }

  return { salvar, isLoading, erroGeral }
}
