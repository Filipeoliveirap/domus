import { api } from '@/lib/api'
import { Endpoints } from '@/lib/endpoints'
import type { PagedResponse } from '@/types/pagedResponse.type'
import type {
  MovimentacaoResponse,
  MovimentacaoRequest,
  MovimentacaoFiltros,
  MovimentacaoTotais,
  MovimentacaoArquivadaResponse,
} from '@/types/financeiro/movimentacao.type'

function paramsDeFiltro(filtros: Omit<MovimentacaoFiltros, 'page' | 'size'>): Record<string, string> {
  const params: Record<string, string> = {}
  if (filtros.tipo) params.tipo = filtros.tipo
  if (filtros.categoriaId) params.categoriaId = filtros.categoriaId
  if (filtros.dataInicio) params.dataInicio = filtros.dataInicio
  if (filtros.dataFim) params.dataFim = filtros.dataFim
  if (filtros.q) params.q = filtros.q
  if (filtros.pessoaId) params.pessoaId = filtros.pessoaId
  return params
}

export const movimentacoesService = {
  listar: async (filtros: MovimentacaoFiltros): Promise<PagedResponse<MovimentacaoResponse>> => {
    const params: Record<string, string | number> = {
      page: filtros.page,
      size: filtros.size ?? 15,
      ...paramsDeFiltro(filtros),
    }

    const { data } = await api.get(Endpoints.movimentacoes.base, { params })
    return data
  },

  totais: async (filtros: Omit<MovimentacaoFiltros, 'page' | 'size'>): Promise<MovimentacaoTotais> => {
    const { data } = await api.get(Endpoints.movimentacoes.totais, { params: paramsDeFiltro(filtros) })
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

  listarArquivadas: async (): Promise<MovimentacaoArquivadaResponse[]> => {
    const { data } = await api.get(Endpoints.movimentacoes.arquivadas)
    return data
  },

  restaurar: async (id: string): Promise<void> => {
    await api.post(Endpoints.movimentacoes.restaurar(id))
  },

  excluirDefinitivo: async (id: string): Promise<void> => {
    await api.delete(Endpoints.movimentacoes.definitivo(id))
  },
}