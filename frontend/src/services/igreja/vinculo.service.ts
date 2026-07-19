import { api } from '@/lib/api'
import { Endpoints } from '@/lib/endpoints'
import type { PeriodoRelatorio } from '@/types/financeiro/relatorio.type'
import type { Consolidado, VinculoStatus } from '@/types/igreja/vinculo.type'

export const vinculoService = {
  status: async (): Promise<VinculoStatus> => {
    const { data } = await api.get(Endpoints.igrejasVinculadas.STATUS)
    return data
  },

  /** Gera ou rotaciona — rotacionar invalida o código anterior. */
  gerarCodigo: async (): Promise<string> => {
    const { data } = await api.post(Endpoints.igrejasVinculadas.GERAR_CODIGO)
    return data.codigoVinculo
  },

  entrar: async (codigo: string): Promise<VinculoStatus> => {
    const { data } = await api.post(Endpoints.igrejasVinculadas.ENTRAR, { codigo })
    return data
  },

  desvincular: async (congregacaoId: string): Promise<void> => {
    await api.delete(Endpoints.igrejasVinculadas.DESVINCULAR(congregacaoId))
  },

  sair: async (): Promise<void> => {
    await api.delete(Endpoints.igrejasVinculadas.SAIR)
  },

  consolidado: async (periodo: PeriodoRelatorio): Promise<Consolidado> => {
    const { data } = await api.get(Endpoints.relatorios.congregacoes, { params: periodo })
    return data
  },
}
