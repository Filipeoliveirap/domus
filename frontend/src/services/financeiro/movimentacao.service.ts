import { api } from '@/lib/api'
import { Endpoints } from '@/lib/endpoints'
import type { PagedResponse } from '@/types/pagedResponse.type'
import type {
  MovimentacaoResponse,
  MovimentacaoRequest,
  MovimentacaoFiltros,
} from '@/types/financeiro/movimentacao.type'

export const movimentacoesService = {
  listar: async (filtros: MovimentacaoFiltros): Promise<PagedResponse<MovimentacaoResponse>> => {
    const params: Record<string, string | number> = {
      page: filtros.page,
      size: filtros.size ?? 15,
    }
    if (filtros.tipo) params.tipo = filtros.tipo
    if (filtros.categoriaId) params.categoriaId = filtros.categoriaId
    if (filtros.dataInicio) params.dataInicio = filtros.dataInicio
    if (filtros.dataFim) params.dataFim = filtros.dataFim
    if (filtros.q) params.q = filtros.q

    const { data } = await api.get(Endpoints.movimentacoes.base, { params })
    return data
  },

  buscar: async (id: string): Promise<MovimentacaoResponse> => {
    const { data } = await api.get(Endpoints.movimentacoes.porId(id))
    return data
  },

  criar: async (payload: MovimentacaoRequest): Promise<MovimentacaoResponse> => {
    const { data } = await api.post(Endpoints.movimentacoes.base, payload)
    return data
  },

  atualizar: async (id: string, payload: MovimentacaoRequest): Promise<MovimentacaoResponse> => {
    const { data } = await api.put(Endpoints.movimentacoes.porId(id), payload)
    return data
  },

  arquivar: async (id: string): Promise<void> => {
    await api.delete(Endpoints.movimentacoes.porId(id))
  },
}