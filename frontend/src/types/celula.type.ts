export type DiaSemana = 'SEGUNDA' | 'TERCA' | 'QUARTA' | 'QUINTA' | 'SEXTA' | 'SABADO' | 'DOMINGO'
export type PapelCelula = 'LIDER' | 'MEMBRO'
export type Vinculo = 'MEMBRO' | 'CONGREGANTE'

export interface CelulaRequest {
  nome: string
  diaSemana?: DiaSemana
  horario?: string
  fotoId?: string | null
}

export interface CelulaResponse {
  id: string
  nome: string
  fotoId: string | null
  diaSemana: DiaSemana | null
  horario: string | null
  lideres: string[]
  totalMembros: number
  souLiderDestaCelula: boolean
  temVinculo: boolean
}

export interface CelulaDetalheResponse {
  id: string
  nome: string
  fotoId: string | null
  diaSemana: DiaSemana | null
  horario: string | null
  membros: MembroCelulaResponse[]
  souLiderDestaCelula: boolean
}

export interface MembroCelulaResponse {
  id: string
  tipo: 'PESSOA' | 'VISITANTE'
  pessoaId: string | null
  visitanteId: string | null
  nome: string
  fotoId: string | null
  papel: PapelCelula
}

export interface AdicionarMembroCelulaRequest {
  pessoaId?: string
  visitanteId?: string
}

export interface ConverterVisitanteRequest {
  vinculo: Vinculo
}
