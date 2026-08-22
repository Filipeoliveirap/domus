import type { PagedResponse } from './pagedResponse.type'
import type { IgrejaResumo } from './evento.type'
import type { RespostaRequest } from './campoPersonalizado.type'

export type CodigoImpedimento =
  | 'FAIXA_ETARIA'
  | 'SEM_DATA_NASCIMENTO'
  | 'EXCLUSIVO_MEMBROS'
  | 'ESTADO_CIVIL'
  | 'SEM_ESTADO_CIVIL'
  | 'SEXO'
  | 'SEM_SEXO'
  | 'VAGAS_ESGOTADAS'

export interface Impedimento {
  codigo: CodigoImpedimento
  mensagem: string
  contornavel: boolean
}

export interface ElegibilidadeResponse {
  apto: boolean
  impedimentos: Impedimento[]
}

export interface AcompanhanteResponse {
  id: string
  nome: string
  telefone: string | null
  compareceu: boolean
}

export interface MinhaInscricaoResponse {
  id: string | null
  inscrito: boolean
  acompanhantes: AcompanhanteResponse[]
}

export interface ParticipanteResponse {
  id: string
  pessoaId: string | null
  nome: string
  fotoId: string | null
  /** Preenchido só pra convidado sem cadastro (inscrição própria com pessoa_id nulo). */
  convidadoPorNome: string | null
  convidados: string[]
  igrejaDaPessoa: IgrejaResumo
}

export interface InscritoResponse {
  id: string
  pessoaId: string | null
  nome: string
  fotoId: string | null
  pessoaRemovida: boolean
  inscritoPorUsuarioId: string | null
  inscritoPorNome: string | null
  inscritoPorFotoId: string | null
  /** Preenchido só pra convidado sem cadastro (inscrição própria com pessoa_id nulo). */
  convidadoPorNome: string | null
  inscritoEm: string
  compareceu: boolean
  acompanhantes: AcompanhanteResponse[]
  igrejaDaPessoa: IgrejaResumo
}

export interface CriarConvidadoRequest {
  nome: string
  telefone?: string
  respostas?: RespostaRequest[]
}

export interface ConvidadoResponse {
  inscricaoId: string
  nome: string
  telefone: string | null
}

export interface ListaInscritosResponse {
  totalPessoas: number
  vagas: number | null
  vagasRestantes: number | null
  inscritos: PagedResponse<InscritoResponse>
}

export interface InscreverPessoasRequest {
  pessoaIds: string[]
}

export interface AcompanhanteRequest {
  nome: string
  telefone?: string
}
