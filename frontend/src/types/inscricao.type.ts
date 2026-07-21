/**
 * Tipos da inscrição em evento. Nomenclatura de backend (tabela/campo `acompanhante*`) é
 * mantida aqui de propósito — só o texto voltado ao usuário usa "convidado" (ver hooks).
 */

export interface AcompanhanteResponse {
  id: string
  nome: string
  telefone: string | null
}

/** O que o próprio usuário vê sobre a sua inscrição no evento. */
export interface MinhaInscricaoResponse {
  id: string | null
  inscrito: boolean
  acompanhantes: AcompanhanteResponse[]
}

/** Linha da lista de participantes visível a qualquer membro — sem telefone nem metadados administrativos. */
export interface ParticipanteResponse {
  id: string
  membroId: string
  nome: string
  foto: string | null
  convidados: string[]
}

/** Linha da lista de inscritos, restrita a ADMIN/LÍDER. */
export interface InscritoResponse {
  id: string
  membroId: string
  nome: string
  foto: string | null
  /** null = a pessoa se inscreveu sozinha. */
  inscritoPorUsuarioId: string | null
  inscritoEm: string
  acompanhantes: AcompanhanteResponse[]
}

export interface ListaInscritosResponse {
  totalPessoas: number
  vagas: number | null
  vagasRestantes: number | null
  inscritos: InscritoResponse[]
}

export interface InscreverMembrosRequest {
  membroIds: string[]
}

export interface AcompanhanteRequest {
  nome: string
  telefone?: string
}
