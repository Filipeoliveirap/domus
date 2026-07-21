import { api } from '@/lib/api'
import { Endpoints } from '@/lib/endpoints'
import type {
  MinhaInscricaoResponse,
  ParticipanteResponse,
  ListaInscritosResponse,
  InscreverMembrosRequest,
  AcompanhanteRequest,
  AcompanhanteResponse,
} from '@/types/inscricao.type'

export const inscricoesService = {
  inscrever: (eventoId: string): Promise<MinhaInscricaoResponse> =>
    api.post<MinhaInscricaoResponse>(Endpoints.inscricoes.INSCREVER(eventoId)).then(res => res.data),

  minhaInscricao: (eventoId: string): Promise<MinhaInscricaoResponse> =>
    api.get<MinhaInscricaoResponse>(Endpoints.inscricoes.MINHA(eventoId)).then(res => res.data),

  inscreverMembros: (eventoId: string, data: InscreverMembrosRequest): Promise<void> =>
    api.post(Endpoints.inscricoes.INSCREVER_MEMBROS(eventoId), data).then(() => undefined),

  adicionarAcompanhante: (
    eventoId: string,
    inscricaoId: string,
    data: AcompanhanteRequest,
  ): Promise<AcompanhanteResponse> =>
    api.post<AcompanhanteResponse>(Endpoints.inscricoes.ACOMPANHANTES(eventoId, inscricaoId), data)
      .then(res => res.data),

  participantes: (eventoId: string): Promise<ParticipanteResponse[]> =>
    api.get<ParticipanteResponse[]>(Endpoints.inscricoes.PARTICIPANTES(eventoId)).then(res => res.data),

  listarInscritos: (eventoId: string): Promise<ListaInscritosResponse> =>
    api.get<ListaInscritosResponse>(Endpoints.inscricoes.LISTAR(eventoId)).then(res => res.data),

  cancelar: (inscricaoId: string): Promise<void> =>
    api.delete(Endpoints.inscricoes.CANCELAR(inscricaoId)).then(() => undefined),

  removerAcompanhante: (acompanhanteId: string): Promise<void> =>
    api.delete(Endpoints.inscricoes.REMOVER_ACOMPANHANTE(acompanhanteId)).then(() => undefined),
}
