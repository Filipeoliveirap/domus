import { api } from '@/lib/api'
import { Endpoints } from '@/lib/endpoints'
import type { EventoRequest, EventoResponse, ImpactoRestricaoResponse } from '@/types/evento.type'
import type { PagedResponse } from '@/types/pagedResponse.type'

interface ListarEventosParams {
  q?: string
  page?: number
  size?: number
  tipo?: string
  recorteEtario?: string
}

export const eventosService = {
  listar: (params: ListarEventosParams): Promise<PagedResponse<EventoResponse>> =>
    api.get<PagedResponse<EventoResponse>>(Endpoints.eventos.LISTAR, {
      params: {
        q: params.q || undefined,
        page: params.page ?? 0,
        size: params.size ?? 12,
        tipo: params.tipo || undefined,
        recorteEtario: params.recorteEtario || undefined,
      },
    }).then(res => res.data),

  buscar: (id: string): Promise<EventoResponse> =>
    api.get<EventoResponse>(Endpoints.eventos.BY_ID(id)).then(res => res.data),

  criar: (data: EventoRequest): Promise<EventoResponse> =>
    api.post<EventoResponse>(Endpoints.eventos.CRIAR, data).then(res => res.data),

  // cancelarNaoElegiveis: escolha do admin no <ModalImpactoRestricao> — default false
  // (mantém todo mundo) espelha o default do backend.
  atualizar: (id: string, data: EventoRequest, cancelarNaoElegiveis = false): Promise<EventoResponse> =>
    api.put<EventoResponse>(Endpoints.eventos.BY_ID(id), data, { params: { cancelarNaoElegiveis } })
      .then(res => res.data),

  arquivar: (id: string): Promise<void> =>
    api.delete(Endpoints.eventos.BY_ID(id)).then(() => undefined),

  // Prévia PURA de impacto (Task 6/9): devolve quem ficaria de fora se `data` fosse salvo,
  // sem gravar nada — só chamada em edição, para decidir se abre o <ModalImpactoRestricao>.
  impactoRestricao: (id: string, data: EventoRequest): Promise<ImpactoRestricaoResponse> =>
    api.post<ImpactoRestricaoResponse>(Endpoints.eventos.IMPACTO_RESTRICAO(id), data).then(res => res.data),

  // Tipos já usados pela igreja (mais frequentes primeiro) seguidos das sementes — a ordem
  // vem pronta do backend, o front só respeita.
  tipos: (): Promise<string[]> =>
    api.get<string[]>(Endpoints.eventos.TIPOS).then(res => res.data),
}