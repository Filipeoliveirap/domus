export interface EventoResponse {
  id: string
  titulo: string
  descricao: string | null
  inicioEm: string
  fimEm: string | null
  local: string | null
  foto: string | null
  createdAt: string
  vagas: number | null
  preco: string | null
  exclusivoMembros: boolean
  exclusivoBatizados: boolean
  requerInscricao: boolean
}

export interface EventoRequest {
  titulo: string
  descricao?: string
  inicioEm: string
  fimEm?: string
  local?: string
  foto?: string
  vagas?: number
  preco?: string
  exclusivoMembros?: boolean
  exclusivoBatizados?: boolean
  requerInscricao?: boolean
}