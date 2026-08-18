import axios from 'axios'
import { useMutation } from '@tanstack/react-query'
import { notificar } from '@/components/common/Notificacao/notificar'
import { fotoService } from '@/services/foto.service'
import type { ApiError } from '@/types/api.types'

// Mensagens de erro de negócio já vêm prontas do backend em `message` — sem switch de tradução aqui.
export function useUploadFoto() {
  return useMutation({
    mutationFn: (arquivo: File) => fotoService.upload(arquivo),
    onError: (error: unknown) => {
      const mensagem = axios.isAxiosError<ApiError>(error)
        ? error.response?.data?.message
        : undefined
      notificar.erro('Não foi possível enviar a foto', mensagem ?? 'Tente novamente.')
    },
  })
}
