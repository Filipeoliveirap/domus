import { api } from '@/lib/api'
import { Endpoints } from '@/lib/endpoints'
import type { VisitanteResponse, VisitanteRequest, ToggleRequest } from '@/types/visitante.type'
import type { PagedResponse } from '@/types/pagedResponse.type'

interface ListarVisitantesParams {
  q?: string
  page?: number
  size?: number
  contatoRealizado?: boolean
  visitaRealizada?: boolean
  acompanhamentoFeito?: boolean
}

export const visitanteService = {
  listar: (params: ListarVisitantesParams): Promise<PagedResponse<VisitanteResponse>> =>
    api.get<PagedResponse<VisitanteResponse>>(Endpoints.visitantes.LISTAR, {
      params: {
        q: params.q || undefined,
        page: params.page ?? 0,
        size: params.size ?? 20,
        contatoRealizado: params.contatoRealizado ?? undefined,
        visitaRealizada: params.visitaRealizada ?? undefined,
        acompanhamentoFeito: params.acompanhamentoFeito ?? undefined,
      },
    }).then(res => res.data),

  buscar: (id: string): Promise<VisitanteResponse> =>
    api.get<VisitanteResponse>(Endpoints.visitantes.BY_ID(id)).then(res => res.data),

  criar: (data: VisitanteRequest): Promise<VisitanteResponse> =>
    api.post<VisitanteResponse>(Endpoints.visitantes.CRIAR, data).then(res => res.data),

  atualizar: (id: string, data: VisitanteRequest): Promise<VisitanteResponse> =>
    api.put<VisitanteResponse>(Endpoints.visitantes.BY_ID(id), data).then(res => res.data),

  excluir: (id: string): Promise<void> =>
    api.delete(Endpoints.visitantes.BY_ID(id)).then(() => undefined),

  toggleContato: (id: string, data: ToggleRequest): Promise<VisitanteResponse> =>
    api.put<VisitanteResponse>(Endpoints.visitantes.TOGGLE_CONTATO(id), data).then(res => res.data),

  toggleVisita: (id: string, data: ToggleRequest): Promise<VisitanteResponse> =>
    api.put<VisitanteResponse>(Endpoints.visitantes.TOGGLE_VISITA(id), data).then(res => res.data),

  toggleAcompanhamento: (id: string, data: ToggleRequest): Promise<VisitanteResponse> =>
    api.put<VisitanteResponse>(Endpoints.visitantes.TOGGLE_ACOMPANHAMENTO(id), data).then(res => res.data),

  moverParaCelula: (id: string, celulaId: string): Promise<VisitanteResponse> =>
    api.put<VisitanteResponse>(Endpoints.visitantes.TOGGLE_CELULA(id), { celulaId }).then(res => res.data),
}
