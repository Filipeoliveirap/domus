import { api } from '@/lib/api'
import { Endpoints } from '@/lib/endpoints'
import type {
  ResumoPeriodo,
  CategoriaBreakdown,
  EvolucaoMensal,
  PeriodoRelatorio,
  MaiorLancamento,
} from '@/types/financeiro/relatorio.type'

export const relatorioService = {
  resumo: async (periodo: PeriodoRelatorio): Promise<ResumoPeriodo> => {
    const { data } = await api.get(Endpoints.relatorios.resumo, { params: periodo })
    return data
  },

  porCategoria: async (periodo: PeriodoRelatorio): Promise<CategoriaBreakdown[]> => {
    const { data } = await api.get(Endpoints.relatorios.porCategoria, { params: periodo })
    return data
  },

  evolucaoMensal: async (periodo: PeriodoRelatorio): Promise<EvolucaoMensal[]> => {
    const { data } = await api.get(Endpoints.relatorios.evolucaoMensal, { params: periodo })
    return data
  },

  maiorLancamento: async (periodo: PeriodoRelatorio): Promise<MaiorLancamento | null> => {
    const { data } = await api.get(Endpoints.relatorios.maiorLancamento, { params: periodo })
    return data
  },
}