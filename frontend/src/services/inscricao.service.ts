import { api } from '@/lib/api'
import { Endpoints } from '@/lib/endpoints'
import type {
  MinhaInscricaoResponse,
  ParticipanteResponse,
  ListaInscritosResponse,
  InscreverPessoasRequest,
  PessoaInscritaComCobranca,
  ElegibilidadeResponse,
  CriarConvidadoRequest,
  ConvidadoResponse,
} from '@/types/inscricao.type'
import type { RespostaRequest, RespostaResponse } from '@/types/campoPersonalizado.type'

export const inscricoesService = {
  /** `confirmado=true` só tem efeito para quem gerencia inscrições — deixa o gestor se
   *  inscrever num recorte fora do seu. De quem não gerencia, o backend ignora. */
  inscrever: (eventoId: string, confirmado = false): Promise<MinhaInscricaoResponse> =>
    api.post<MinhaInscricaoResponse>(Endpoints.inscricoes.INSCREVER(eventoId), null, { params: { confirmado } })
      .then(res => res.data),

  minhaInscricao: (eventoId: string): Promise<MinhaInscricaoResponse> =>
    api.get<MinhaInscricaoResponse>(Endpoints.inscricoes.MINHA(eventoId)).then(res => res.data),

  /** Elegibilidade da PRÓPRIA PESSOA logada — conveniência de UX, nunca defesa (ver DTO no back). */
  elegibilidade: (eventoId: string): Promise<ElegibilidadeResponse> =>
    api.get<ElegibilidadeResponse>(Endpoints.eventos.ELEGIBILIDADE(eventoId)).then(res => res.data),

  /**
   * `confirmado=true` só tem efeito para quem `podeGerenciarInscricoes` — de quem não
   * gerencia o backend ignora o parâmetro (Regra 2 do InscricaoService).
   */
  inscreverPessoas: (
    eventoId: string, data: InscreverPessoasRequest, confirmado = false,
  ): Promise<PessoaInscritaComCobranca[]> =>
    api.post<PessoaInscritaComCobranca[]>(Endpoints.inscricoes.INSCREVER_MEMBROS(eventoId), data, { params: { confirmado } })
      .then(res => res.data),

  criarConvidado: (eventoId: string, data: CriarConvidadoRequest): Promise<ConvidadoResponse> =>
    api.post<ConvidadoResponse>(Endpoints.inscricoes.CONVIDADOS(eventoId), data).then(res => res.data),

  participantes: (eventoId: string): Promise<ParticipanteResponse[]> =>
    api.get<ParticipanteResponse[]>(Endpoints.inscricoes.PARTICIPANTES(eventoId)).then(res => res.data),

  listarInscritos: (eventoId: string, busca?: string, page = 0, size?: number): Promise<ListaInscritosResponse> =>
    api.get<ListaInscritosResponse>(Endpoints.inscricoes.LISTAR(eventoId), {
      params: { busca: busca || undefined, page, size },
    }).then(res => res.data),

  cancelar: (inscricaoId: string): Promise<void> =>
    api.delete(Endpoints.inscricoes.CANCELAR(inscricaoId)).then(() => undefined),

  respostas: (inscricaoId: string): Promise<RespostaResponse[]> =>
    api.get<RespostaResponse[]>(Endpoints.inscricoes.RESPOSTAS(inscricaoId)).then(res => res.data),

  responder: (inscricaoId: string, dados: RespostaRequest[]): Promise<void> =>
    api.put(Endpoints.inscricoes.RESPOSTAS(inscricaoId), dados).then(() => undefined),

  marcarTodosPresentes: (eventoId: string): Promise<void> =>
    api.post(Endpoints.presenca.MARCAR_TODOS(eventoId)).then(() => undefined),

  desmarcarTodosPresentes: (eventoId: string): Promise<void> =>
    api.post(Endpoints.presenca.DESMARCAR_TODOS(eventoId)).then(() => undefined),

  marcarPresencaInscricao: (eventoId: string, inscricaoId: string, compareceu: boolean): Promise<void> =>
    api.patch(Endpoints.presenca.INSCRICAO(eventoId, inscricaoId), { compareceu })
      .then(() => undefined),

  enviarLembretePagamento: (eventoId: string, inscricaoId: string): Promise<void> =>
    api.post(Endpoints.inscricoes.LEMBRETE_PAGAMENTO(eventoId, inscricaoId)).then(() => undefined),
}
