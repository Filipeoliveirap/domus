'use client'

import { useQuery } from '@tanstack/react-query'
import { eventosService } from '@/services/evento.service'
import type { RelatorioGeralFiltros } from '@/types/evento.type'

export function useRelatorioGeral(filtros: RelatorioGeralFiltros) {
  return useQuery({
    queryKey: ['relatorio-geral', filtros],
    queryFn: () => eventosService.relatorioGeral(filtros),
  })
}
