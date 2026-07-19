import { api } from '@/lib/api'
import { Endpoints } from '@/lib/endpoints'
import type {
  ResumoPeriodo,
  CategoriaBreakdown,
  EvolucaoMensal,
  PeriodoRelatorio,
  MaiorLancamento,
} from '@/types/financeiro/relatorio.type'

/**
 * `igrejaId` opcional: ausente = minha igreja (comportamento de sempre); presente = uma
 * congregação da minha família. Quem valida se eu posso vê-la é o backend — o front só
 * repassa a escolha do seletor.
 */
export const relatorioService = {
  resumo: async (periodo: PeriodoRelatorio, igrejaId?: string): Promise<ResumoPeriodo> => {
    const { data } = await api.get(Endpoints.relatorios.resumo, { params: { ...periodo, ...(igrejaId ? { igrejaId } : {}) } })
    return data
  },

  porCategoria: async (periodo: PeriodoRelatorio, igrejaId?: string): Promise<CategoriaBreakdown[]> => {
    const { data } = await api.get(Endpoints.relatorios.porCategoria, { params: { ...periodo, ...(igrejaId ? { igrejaId } : {}) } })
    return data
  },

  evolucaoMensal: async (periodo: PeriodoRelatorio, igrejaId?: string): Promise<EvolucaoMensal[]> => {
    const { data } = await api.get(Endpoints.relatorios.evolucaoMensal, { params: { ...periodo, ...(igrejaId ? { igrejaId } : {}) } })
    return data
  },

  maiorLancamento: async (periodo: PeriodoRelatorio, igrejaId?: string): Promise<MaiorLancamento | null> => {
    const { data } = await api.get(Endpoints.relatorios.maiorLancamento, { params: { ...periodo, ...(igrejaId ? { igrejaId } : {}) } })
    return data
  },
}