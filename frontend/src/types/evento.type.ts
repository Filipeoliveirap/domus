export interface EventoResponse {
  id: string
  titulo: string
  descricao: string | null
  inicioEm: string       
  fimEm: string | null
  local: string | null
  foto: string | null
  createdAt: string
}

export interface EventoRequest {
  titulo: string
  descricao?: string
  inicioEm: string        
  fimEm?: string
  local?: string
  foto?: string
}