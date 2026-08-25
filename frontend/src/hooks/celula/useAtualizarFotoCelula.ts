import { useMutation, useQueryClient } from '@tanstack/react-query'
import { celulaService } from '@/services/celula.service'
import { invalidarCache } from '@/lib/cacheInvalidacao'

/**
 * Salva só a foto da célula — dispara assim que o UploadFoto confirma o
 * recorte (ou remove a foto), sem esperar o resto do formulário ser salvo.
 * Só faz sentido em edição: uma célula nova ainda não tem id.
 */
export function useAtualizarFotoCelula(celulaId: string | undefined) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (fotoId: string | null) => celulaService.atualizarFoto(celulaId!, fotoId),
    onSuccess: () => invalidarCache(queryClient, 'celula'),
  })
}
