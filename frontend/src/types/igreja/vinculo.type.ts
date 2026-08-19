export type EstadoVinculo = 'INDEPENDENTE' | 'MAE' | 'FILHA'

export interface IgrejaResumo {
  id: string
  nome: string
  /** Vêm do endereço da igreja, que é opcional — por isso podem ser nulos. */
  cidade: string | null
  uf: string | null
  /** Quando esta igreja entrou na família. Nulo para quem não é congregação. */
  vinculadoEm: string | null
}

// Os três estados são mutuamente exclusivos (regra dos 2 níveis garantida no backend).
export interface VinculoStatus {
  estado: EstadoVinculo
  codigoVinculo: string | null
  /** Permite a tela dizer "gerado em ..." e sugerir rotação — o código não expira. */
  codigoGeradoEm: string | null
  mae: IgrejaResumo | null
  congregacoes: IgrejaResumo[]
}

export interface PessoasConsolidado {
  total: number
  membros: number
  congregantes: number
}

export interface EventosConsolidado {
  total: number
  realizados: number
  proximos: number
}

export interface FinanceiroConsolidado {
  entradas: string
  saidas: string
  saldo: string
}

export interface TotaisConsolidado {
  pessoas: PessoasConsolidado
  eventos: EventosConsolidado
  financeiro: FinanceiroConsolidado
}

export interface LinhaIgrejaConsolidado extends TotaisConsolidado {
  igrejaId: string
  nome: string
  ehMae: boolean
}

export interface Consolidado {
  familia: TotaisConsolidado
  porIgreja: LinhaIgrejaConsolidado[]
}
