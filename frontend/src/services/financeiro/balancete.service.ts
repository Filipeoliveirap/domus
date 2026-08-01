import { api } from '@/lib/api'
import { Endpoints } from '@/lib/endpoints'
import type { Balancete, BalanceteFamilia } from '@/types/financeiro/balancete.type'

export const balanceteService = {
  anual: async (ano: number): Promise<Balancete> => {
    const { data } = await api.get(Endpoints.relatorios.balanceteAnual, { params: { ano } })
    return data
  },

  familia: async (ano: number): Promise<BalanceteFamilia> => {
    const { data } = await api.get(Endpoints.relatorios.balanceteFamilia, { params: { ano } })
    return data
  },
}
