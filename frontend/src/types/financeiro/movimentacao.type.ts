export type TipoMovimentacao = 'ENTRADA' | 'SAIDA'

export interface ContribuinteResponse {
  pessoaId: string | null
  pessoaNome: string
  /** Só vem preenchido quando é pessoa de fora (sem cadastro) — distingue esse caso de
   *  "pessoa cadastrada mas já excluída definitivamente" (pessoaId também nulo nos dois). */
  nomeExterno: string | null
  valor: string
}

export interface ContribuinteInput {
  pessoaId: string | null
  nomeExterno: string | null
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
  arquivada: boolean
}

export interface MovimentacaoRequest {
  tipo: TipoMovimentacao
  valor: string
  categoriaId: string
  dataMovimentacao: string
  contribuintes: ContribuinteInput[]
  descricao?: string | null
}

export interface MovimentacaoArquivadaResponse {
  id: string
  descricao: string | null
  tipo: TipoMovimentacao
  valor: string
  dataMovimentacao: string
  temContribuinte: boolean
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