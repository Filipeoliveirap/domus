import type { CampoPersonalizadoResponse, RespostaRequest } from './campoPersonalizado.type'
import type { SituacaoInscricao } from './evento.type'

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
  requerInscricao: boolean
  situacaoInscricao: SituacaoInscricao
  inscricoesAte: string | null
}

export interface EntrarConviteRequest {
  nome: string
  telefone?: string
  /** Opcional em evento gratuito, obrigatório em evento pago (usado pra mandar o
   *  comprovante de pagamento) — o backend recusa sem isso quando o evento tem preço. */
  email?: string
  respostas?: RespostaRequest[]
}
