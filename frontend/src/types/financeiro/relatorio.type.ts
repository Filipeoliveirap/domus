import type { TipoMovimentacao } from './movimentacao.type'

export interface VariacaoPercentual {
  entradasVariacao: string
  saidasVariacao: string
  saldoVariacao: string
}

export interface ResumoPeriodo {
  totalEntradas: string
  totalSaidas: string
  saldo: string
  entradasAnterior: string
  saidasAnterior: string
  saldoAnterior: string
  quantidadeMovimentacoes: number
  comparacao: VariacaoPercentual
}

export interface CategoriaBreakdown {
  categoriaId: string
  categoriaNome: string
  tipo: TipoMovimentacao
  total: string
  percentual: string
}

export interface EvolucaoMensal {
  ano: number
  mes: number          
  entradas: string
  saidas: string
  saldo: string
}

export interface PeriodoRelatorio {
  dataInicio: string   
  dataFim: string      
}

export interface ContribuinteBreakdown {
  pessoaId: string
  pessoaNome: string
  tipo: TipoMovimentacao
  total: string
  percentual: string
}

export interface MaiorLancamento {
  id: string
  descricao: string | null
  categoriaNome: string
  tipo: TipoMovimentacao
  valor: string
  dataMovimentacao: string
}