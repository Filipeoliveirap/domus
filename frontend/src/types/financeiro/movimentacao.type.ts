export type TipoMovimentacao = 'ENTRADA' | 'SAIDA'

export interface MovimentacaoResponse {
  id: string
  tipo: TipoMovimentacao
  valor: string 
  dataMovimentacao: string        
  descricao: string | null
  categoriaId: string
  categoriaNome: string
  membroId: string | null
  membroNome: string | null
  criadoPorNome: string
  atualizadoPorNome: string | null
}

export interface MovimentacaoRequest {
  tipo: TipoMovimentacao
  valor: string 
  categoriaId: string
  dataMovimentacao: string        
  membroId?: string | null
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