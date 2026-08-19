import { api } from '@/lib/api'
import { Endpoints } from '@/lib/endpoints'
import type { FotoResponse } from '@/types/foto.types'

export const fotoService = {
  // Content-Type: undefined é necessário — o default 'application/json' da instância axios sufoca o boundary do multipart, e sem boundary o Spring rejeita a requisição.
  upload: (arquivo: File): Promise<FotoResponse> => {
    const formData = new FormData()
    formData.append('arquivo', arquivo)
    return api
      .post<FotoResponse>(Endpoints.fotos.UPLOAD, formData, {
        headers: { 'Content-Type': undefined },
      })
      .then((res) => res.data)
  },
}
