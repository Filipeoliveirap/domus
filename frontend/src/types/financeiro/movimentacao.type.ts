export type TipoMovimentacao = 'ENTRADA' | 'SAIDA'

export interface MovimentacaoResponse {
  id: string
  tipo: TipoMovimentacao
  valor: string 
  dataMovimentacao: string        
  descricao: string | null
  categoriaId: string
  categoriaNome: string
  pessoaId: string | null
  // Nome vem assim da API (`MovimentacaoResponse.pessoaNome`, backend) — não renomeado por lá.
  pessoaNome: string | null
  criadoPorNome: string
  atualizadoPorNome: string | null
}

export interface MovimentacaoRequest {
  tipo: TipoMovimentacao
  valor: string
  categoriaId: string
  dataMovimentacao: string
  pessoaId?: string | null
  descricao?: string | null
}

export interface MovimentacaoFiltros {
  tipo?: TipoMovimentacao
  categoriaId?: string
  dataInicio?: string
  dataFim?: string
  q?: string
  page: number
  size?: number
}