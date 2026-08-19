'use client'

import { useQuery } from '@tanstack/react-query'
import { eventosService } from '@/services/evento.service'

export function useRelatorioEvento(eventoId: string | undefined, enabled = true) {
  return useQuery({
    queryKey: ['relatorio-evento', eventoId],
    queryFn: () => eventosService.relatorio(eventoId!),
    enabled: !!eventoId && enabled,
  })
}
