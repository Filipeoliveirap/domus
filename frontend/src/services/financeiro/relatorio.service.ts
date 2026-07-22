import { api } from '@/lib/api'
import { Endpoints } from '@/lib/endpoints'
import type {
  ResumoPeriodo,
  CategoriaBreakdown,
  EvolucaoMensal,
  PeriodoRelatorio,
  MaiorLancamento,
} from '@/types/financeiro/relatorio.type'
import type { Vinculo } from '@/types/pessoa.type'

/**
 * `igrejaId` opcional: ausente = minha igreja (comportamento de sempre); presente = uma
 * congregação da minha família. Quem valida se eu posso vê-la é o backend — o front só
 * repassa a escolha do seletor.
 *
 * `vinculo` opcional: filtra as contribuições pelo vínculo de quem contribuiu (Membros
 * ou Congregantes). Ausente = sem filtro, mesmo comportamento de sempre.
 */
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
}