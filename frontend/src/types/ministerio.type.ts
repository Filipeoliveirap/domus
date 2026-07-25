export type Papel = 'LIDER' | 'MEMBRO'

export interface MinisterioRequest {
  nome: string
}

export interface MinisterioResponse {
  id: string
  nome: string
  /** Vazio em respostas que não consultam membros (ex.: GET /pessoas/{id}/ministerios). */
  lideres: string[]
  totalMembros: number
}

export interface MembroResponse {
  pessoaId: string
  nome: string
  fotoId: string | null
  papel: Papel
}

export interface MinisterioDetalheResponse {
  id: string
  nome: string
  membros: MembroResponse[]
  pedidosPendentes: MembroResponse[]
  souLiderDesteMinisterio: boolean
  souMembroAtivo: boolean
  tenhoPedidoPendente: boolean
}
