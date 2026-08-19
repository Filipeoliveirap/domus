import { api } from '@/lib/api'
import { Endpoints } from '@/lib/endpoints'
import type { PagedResponse } from '@/types/pagedResponse.type'
import type { CategoriaResponse, CategoriaRequest } from '@/types/financeiro/categoria.type'

interface ListarParams {
  q?: string
  page?: number
  size?: number
}

export const categoriasService = {
  listar: async (params: ListarParams): Promise<PagedResponse<CategoriaResponse>> => {
    const { data } = await api.get(Endpoints.categorias.base, { params })
    return data
  },

  listarTodas: async (): Promise<CategoriaResponse[]> => {
    const { data } = await api.get(Endpoints.categorias.todas)
    return data
  },

  buscar: async (id: string): Promise<CategoriaResponse> => {
    const { data } = await api.get(Endpoints.categorias.porId(id))
    return data
  },

  criar: async (payload: CategoriaRequest): Promise<CategoriaResponse> => {
    const { data } = await api.post(Endpoints.categorias.base, payload)
    return data
  },

  atualizar: async (id: string, payload: CategoriaRequest): Promise<CategoriaResponse> => {
    const { data } = await api.put(Endpoints.categorias.porId(id), payload)
    return data
  },

  arquivar: async (id: string): Promise<void> => {
    await api.delete(Endpoints.categorias.porId(id))
  },

  listarArquivadas: async (): Promise<CategoriaResponse[]> => {
    const { data } = await api.get(Endpoints.categorias.arquivadas)
    return data
  },

  restaurar: async (id: string): Promise<void> => {
    await api.post(Endpoints.categorias.restaurar(id))
  },

  excluirDefinitivo: async (id: string): Promise<void> => {
    await api.delete(Endpoints.categorias.definitivo(id))
  },

  // A11/rodada 3: quantos lançamentos usam a categoria — consultado só ao abrir a edição,
  // para decidir se pede confirmação de que a mudança vale para todos eles.
  contarMovimentacoes: async (id: string): Promise<number> => {
    const { data } = await api.get<{ total: number }>(Endpoints.categorias.contagemMovimentacoes(id))
    return data.total
  },
}