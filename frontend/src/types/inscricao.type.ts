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

/** Linha da lista de participantes visível a qualquer pessoa inscrita — sem telefone nem metadados administrativos. */
export interface ParticipanteResponse {
  id: string
  pessoaId: string
  nome: string
  fotoId: string | null
  convidados: string[]
}

/** Linha da lista de inscritos, restrita a ADMIN/LÍDER. */
export interface InscritoResponse {
  id: string
  pessoaId: string
  nome: string
  fotoId: string | null
  /** null = a pessoa se inscreveu sozinha. */
  inscritoPorUsuarioId: string | null
  /**
   * Nome de quem inscreveu. null quando `inscritoPorUsuarioId` também é null
   * (auto-inscrição) OU quando a conta de quem inscreveu foi arquivada depois — nesse
   * segundo caso o id continua presente, mas sem nome pra exibir.
   */
  inscritoPorNome: string | null
  inscritoPorFotoId: string | null
  inscritoEm: string
  acompanhantes: AcompanhanteResponse[]
}

export interface ListaInscritosResponse {
  totalPessoas: number
  vagas: number | null
  vagasRestantes: number | null
  inscritos: InscritoResponse[]
}

export interface InscreverPessoasRequest {
  pessoaIds: string[]
}

export interface AcompanhanteRequest {
  nome: string
  telefone?: string
}
