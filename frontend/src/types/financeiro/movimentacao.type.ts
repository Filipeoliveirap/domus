export type TipoMovimentacao = 'ENTRADA' | 'SAIDA'

export interface ContribuinteResponse {
  pessoaId: string
  pessoaNome: string
  valor: string
}

export interface ContribuinteInput {
  pessoaId: string
  valor: string
}

export interface MovimentacaoResponse {
  id: string
  tipo: TipoMovimentacao
  valor: string
  dataMovimentacao: string
  descricao: string | null
  categoriaId: string
  categoriaNome: string
  contribuintes: ContribuinteResponse[]
  criadoPorNome: string
  atualizadoPorNome: string | null
}

export interface MovimentacaoRequest {
  tipo: TipoMovimentacao
  valor: string
  categoriaId: string
  dataMovimentacao: string
  contribuintes: ContribuinteInput[]
  descricao?: string | null
}

export interface MovimentacaoTotais {
  totalEntradas: string
  totalSaidas: string
  quantidade: number
}

export interface MovimentacaoFiltros {
  tipo?: TipoMovimentacao
  categoriaId?: string
  dataInicio?: string
  dataFim?: string
  q?: string
  pessoaId?: string
  page: number
  size?: number
}