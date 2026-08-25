import { useMutation, useQueryClient } from '@tanstack/react-query'
import { eventosService } from '@/services/evento.service'
import type { EventoResponse } from '@/types/evento.type'

/**
 * Salva só a imagem do evento — dispara assim que o UploadFoto confirma o
 * recorte (ou remove a foto), sem esperar o resto do formulário ser salvo.
 * Só faz sentido em edição: um evento novo ainda não tem id.
 */
export function useAtualizarFotoEvento(eventoId: string | undefined) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (fotoId: string | null) => eventosService.atualizarFoto(eventoId!, fotoId),
    onSuccess: (_, fotoId) => {
      queryClient.setQueryData<EventoResponse | undefined>(['evento', eventoId], (atual) =>
        atual ? { ...atual, fotoId } : atual,
      )
    },
  })
}
