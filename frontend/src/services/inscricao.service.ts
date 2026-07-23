import { api } from '@/lib/api'
import { Endpoints } from '@/lib/endpoints'
import type {
  MinhaInscricaoResponse,
  ParticipanteResponse,
  ListaInscritosResponse,
  InscreverPessoasRequest,
  AcompanhanteRequest,
  AcompanhanteResponse,
  ElegibilidadeResponse,
} from '@/types/inscricao.type'

export const inscricoesService = {
  inscrever: (eventoId: string): Promise<MinhaInscricaoResponse> =>
    api.post<MinhaInscricaoResponse>(Endpoints.inscricoes.INSCREVER(eventoId)).then(res => res.data),

  minhaInscricao: (eventoId: string): Promise<MinhaInscricaoResponse> =>
    api.get<MinhaInscricaoResponse>(Endpoints.inscricoes.MINHA(eventoId)).then(res => res.data),

  /** Elegibilidade da PRÓPRIA PESSOA logada — conveniência de UX, nunca defesa (ver DTO no back). */
  elegibilidade: (eventoId: string): Promise<ElegibilidadeResponse> =>
    api.get<ElegibilidadeResponse>(Endpoints.eventos.ELEGIBILIDADE(eventoId)).then(res => res.data),

  /**
   * `confirmado=true` só tem efeito para quem `podeGerenciarInscricoes` — de quem não
   * gerencia o backend ignora o parâmetro (Regra 2 do InscricaoService).
   */
  inscreverPessoas: (eventoId: string, data: InscreverPessoasRequest, confirmado = false): Promise<void> =>
    api.post(Endpoints.inscricoes.INSCREVER_MEMBROS(eventoId), data, { params: { confirmado } })
      .then(() => undefined),

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
