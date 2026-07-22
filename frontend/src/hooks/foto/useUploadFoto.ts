import axios from 'axios'
import { useMutation } from '@tanstack/react-query'
import { notificar } from '@/components/common/Notificacao/notificar'
import { fotoService } from '@/services/foto.service'
import type { ApiError } from '@/types/api.types'

/**
 * Envia uma foto (membro, evento ou logo da igreja) e devolve o id gerado pela API — é
 * esse id que o formulário guarda e que depois vira `urlFoto(id)` para exibir a imagem.
 *
 * <p>Os códigos de erro de negócio (`FORMATO_NAO_ACEITO`, `ARQUIVO_INVALIDO`,
 * `IMAGEM_GRANDE_DEMAIS`, `FALHA_PROCESSAMENTO`) já chegam do backend com texto pronto
 * para o usuário em `message` — por isso não há um `switch` de tradução aqui.
 */
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
