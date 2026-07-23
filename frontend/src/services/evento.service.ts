import { api } from '@/lib/api'
import { Endpoints } from '@/lib/endpoints'
import type { EventoRequest, EventoResponse } from '@/types/evento.type'
import type { PagedResponse } from '@/types/pagedResponse.type'

interface ListarEventosParams {
  q?: string
  page?: number
  size?: number
}

export const eventosService = {
  listar: (params: ListarEventosParams): Promise<PagedResponse<EventoResponse>> =>
    api.get<PagedResponse<EventoResponse>>(Endpoints.eventos.LISTAR, {
      params: {
        q: params.q || undefined,
        page: params.page ?? 0,
        size: params.size ?? 12,
      },
    }).then(res => res.data),

  buscar: (id: string): Promise<EventoResponse> =>
    api.get<EventoResponse>(Endpoints.eventos.BY_ID(id)).then(res => res.data),

  criar: (data: EventoRequest): Promise<EventoResponse> =>
    api.post<EventoResponse>(Endpoints.eventos.CRIAR, data).then(res => res.data),

  atualizar: (id: string, data: EventoRequest): Promise<EventoResponse> =>
    api.put<EventoResponse>(Endpoints.eventos.BY_ID(id), data).then(res => res.data),

  arquivar: (id: string): Promise<void> =>
    api.delete(Endpoints.eventos.BY_ID(id)).then(() => undefined),

  // Tipos já usados pela igreja (mais frequentes primeiro) seguidos das sementes — a ordem
  // vem pronta do backend, o front só respeita.
  tipos: (): Promise<string[]> =>
    api.get<string[]>(Endpoints.eventos.TIPOS).then(res => res.data),
}