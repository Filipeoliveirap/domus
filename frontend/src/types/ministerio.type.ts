export type Papel = 'LIDER' | 'MEMBRO'

export interface MinisterioRequest {
  nome: string
  fotoId?: string | null
}

export interface MinisterioResponse {
  id: string
  nome: string
  fotoId: string | null
  /** Vazio em respostas que não consultam membros (ex.: GET /pessoas/{id}/ministerios). */
  lideres: string[]
  totalMembros: number
  /** true quando a pessoa logada é líder desta rede — habilita o menu de ação no card. */
  souLiderDesteMinisterio: boolean
  temVinculo: boolean
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
  fotoId: string | null
  membros: MembroResponse[]
  pedidosPendentes: MembroResponse[]
  souLiderDesteMinisterio: boolean
  souMembroAtivo: boolean
  tenhoPedidoPendente: boolean
  arquivada: boolean
}
