import type { CampoPersonalizadoResponse, RespostaRequest } from './campoPersonalizado.type'

export interface GerarConviteResponse {
  token: string
  link: string
}

export interface ConvitePublico {
  eventoId: string
  titulo: string
  descricao: string | null
  inicioEm: string
  fimEm: string | null
  localNome: string | null
  localEndereco: string | null
  fotoId: string | null
  igrejaNome: string
  igrejaLogoFotoId: string | null
  convidadoPorNome: string | null
  convidadoPorFotoId: string | null
  vagasRestantes: number | null
  preco: number | null
  campos: CampoPersonalizadoResponse[]
}

export interface EntrarConviteRequest {
  nome: string
  telefone?: string
  respostas?: RespostaRequest[]
}
