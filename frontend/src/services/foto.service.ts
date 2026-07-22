import { api } from '@/lib/api'
import { Endpoints } from '@/lib/endpoints'
import type { FotoResponse } from '@/types/foto.types'

export const fotoService = {
  /**
   * Envia o arquivo como `multipart/form-data`, campo `arquivo`. NÃO defina o header
   * `Content-Type` manualmente aqui: o navegador precisa gerar o boundary do multipart
   * sozinho, e um header fixo o substitui e quebra o upload.
   */
  upload: (arquivo: File): Promise<FotoResponse> => {
    const formData = new FormData()
    formData.append('arquivo', arquivo)
    return api.post<FotoResponse>(Endpoints.fotos.UPLOAD, formData).then((res) => res.data)
  },
}
