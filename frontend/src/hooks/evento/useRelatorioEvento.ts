'use client'

import { useQuery } from '@tanstack/react-query'
import { eventosService } from '@/services/evento.service'

/**
 * Relatório individual de presença — só faz sentido pedir quando `evento.controlaPresenca`
 * é true (senão a seção de comparecimento nem existiria na resposta); `enabled` deixa o
 * chamador decidir isso pela própria situação do evento, sem disparar a query à toa.
 */
export function useRelatorioEvento(eventoId: string | undefined, enabled = true) {
  return useQuery({
    queryKey: ['relatorio-evento', eventoId],
    queryFn: () => eventosService.relatorio(eventoId!),
    enabled: !!eventoId && enabled,
  })
}
