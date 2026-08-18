import { api } from '@/lib/api'
import { Endpoints } from '@/lib/endpoints'
import type {
  ResumoPeriodo,
  CategoriaBreakdown,
  EvolucaoMensal,
  PeriodoRelatorio,
  MaiorLancamento,
  ContribuinteBreakdown,
} from '@/types/financeiro/relatorio.type'
import type { Vinculo } from '@/types/pessoa.type'

// igrejaId ausente = minha igreja; presente = congregação da família (backend valida o acesso).
export const relatorioService = {
  resumo: async (periodo: PeriodoRelatorio, igrejaId?: string, vinculo?: Vinculo | ''): Promise<ResumoPeriodo> => {
    const { data } = await api.get(Endpoints.relatorios.resumo, { params: { ...periodo, ...(igrejaId ? { igrejaId } : {}), ...(vinculo ? { vinculo } : {}) } })
    return data
  },

  porCategoria: async (periodo: PeriodoRelatorio, igrejaId?: string, vinculo?: Vinculo | ''): Promise<CategoriaBreakdown[]> => {
    const { data } = await api.get(Endpoints.relatorios.porCategoria, { params: { ...periodo, ...(igrejaId ? { igrejaId } : {}), ...(vinculo ? { vinculo } : {}) } })
    return data
  },

  evolucaoMensal: async (periodo: PeriodoRelatorio, igrejaId?: string, vinculo?: Vinculo | ''): Promise<EvolucaoMensal[]> => {
    const { data } = await api.get(Endpoints.relatorios.evolucaoMensal, { params: { ...periodo, ...(igrejaId ? { igrejaId } : {}), ...(vinculo ? { vinculo } : {}) } })
    return data
  },

  maiorLancamento: async (periodo: PeriodoRelatorio, igrejaId?: string, vinculo?: Vinculo | ''): Promise<MaiorLancamento | null> => {
    const { data } = await api.get(Endpoints.relatorios.maiorLancamento, { params: { ...periodo, ...(igrejaId ? { igrejaId } : {}), ...(vinculo ? { vinculo } : {}) } })
    return data
  },

  porContribuinte: async (periodo: PeriodoRelatorio, igrejaId?: string, vinculo?: Vinculo | ''): Promise<ContribuinteBreakdown[]> => {
    const { data } = await api.get(Endpoints.relatorios.porContribuinte, { params: { ...periodo, ...(igrejaId ? { igrejaId } : {}), ...(vinculo ? { vinculo } : {}) } })
    return data
  },
}