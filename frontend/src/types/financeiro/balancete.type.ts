export interface LinhaCategoria {
  categoriaId: string | null
  nomeCategoria: string
  arquivada: boolean
  valoresPorMes: string[] // 12 posições, jan..dez
  totalAno: string
}

export interface Balancete {
  ano: number
  saldoAbertura: string
  entradas: LinhaCategoria[]
  saidas: LinhaCategoria[]
  subtotalEntradasPorMes: string[]
  subtotalSaidasPorMes: string[]
  saldoDoMes: string[]
  saldoAcumulado: string[]
}

export interface BalanceteIgreja {
  igrejaId: string
  nomeIgreja: string
  ehSede: boolean
  balancete: Balancete
}

export interface BalanceteFamilia {
  porIgreja: BalanceteIgreja[]
  consolidado: Balancete
}
