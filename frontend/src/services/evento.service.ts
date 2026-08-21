import { api } from '@/lib/api'
import { Endpoints } from '@/lib/endpoints'
import type {
  EventoArquivadoResponse, EventoRequest, EventoResponse, ImpactoRestricaoResponse,
  RelatorioEventoResponse, RelatorioGeralResponse, RelatorioGeralFiltros, EscopoEdicaoEvento,
} from '@/types/evento.type'
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
  // (mantém todo mundo) espelha o default do backend. escopo: só relevante quando o evento
  // pertence a uma série (serieId != null) — default ESTA no backend cobre evento avulso.
  atualizar: (id: string, data: EventoRequest, cancelarNaoElegiveis = false, escopo?: EscopoEdicaoEvento): Promise<EventoResponse> =>
    api.put<EventoResponse>(Endpoints.eventos.BY_ID(id), data, {
      params: { cancelarNaoElegiveis, escopo },
    }).then(res => res.data),

  arquivar: (id: string, escopo?: EscopoEdicaoEvento): Promise<void> =>
    api.delete(Endpoints.eventos.BY_ID(id), { params: { escopo } }).then(() => undefined),

  listarArquivados: (): Promise<EventoArquivadoResponse[]> =>
    api.get<EventoArquivadoResponse[]>(Endpoints.eventos.ARQUIVADOS).then(res => res.data),

  restaurar: (id: string, escopo?: EscopoEdicaoEvento): Promise<void> =>
    api.post(Endpoints.eventos.RESTAURAR(id), null, { params: { escopo } }).then(() => undefined),

  excluirDefinitivo: (id: string): Promise<void> =>
    api.delete(Endpoints.eventos.DEFINITIVO(id)).then(() => undefined),

  // Prévia PURA de impacto (Task 6/9): devolve quem ficaria de fora se `data` fosse salvo,
  // sem gravar nada — só chamada em edição, para decidir se abre o <ModalImpactoRestricao>.
  impactoRestricao: (id: string, data: EventoRequest): Promise<ImpactoRestricaoResponse> =>
    api.post<ImpactoRestricaoResponse>(Endpoints.eventos.IMPACTO_RESTRICAO(id), data).then(res => res.data),

  // Tipos já usados pela igreja (mais frequentes primeiro) seguidos das sementes — a ordem
  // vem pronta do backend, o front só respeita.
  tipos: (): Promise<string[]> =>
    api.get<string[]>(Endpoints.eventos.TIPOS).then(res => res.data),

  relatorio: (id: string): Promise<RelatorioEventoResponse> =>
    api.get<RelatorioEventoResponse>(Endpoints.eventos.RELATORIO(id)).then(res => res.data),

  relatorioGeral: (filtros: RelatorioGeralFiltros): Promise<RelatorioGeralResponse> =>
    api.get<RelatorioGeralResponse>(Endpoints.eventos.RELATORIO_GERAL, {
      params: {
        inicio: filtros.inicio || undefined,
        fim: filtros.fim || undefined,
        recorteEtario: filtros.recorteEtario || undefined,
        tipo: filtros.tipo || undefined,
        page: filtros.page ?? undefined,
      },
    }).then(res => res.data),
}