import { api } from '@/lib/api'
import { Endpoints } from '@/lib/endpoints'
import type { FotoResponse } from '@/types/foto.types'

export const fotoService = {
  /**
   * Envia o arquivo como `multipart/form-data`, campo `arquivo`.
   *
   * O `Content-Type: undefined` não é descuido — é o conserto. A instância do axios
   * (`lib/api.ts`) define `application/json` como padrão para TODA requisição, e esse
   * padrão também cai em cima do `FormData`. Quando isso acontece, o navegador não gera
   * o *boundary* do multipart, e sem boundary o Spring não consegue separar os campos:
   * responde `Current request is not a multipart request`.
   *
   * Anulando o header aqui, o axios deixa o navegador defini-lo — e ele o escreve
   * completo, com o boundary. NUNCA escreva `multipart/form-data` à mão: o boundary é
   * gerado na hora do envio e não há como adivinhá-lo.
   */
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
