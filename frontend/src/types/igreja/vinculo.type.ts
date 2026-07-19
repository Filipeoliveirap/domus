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

/**
 * Os três estados são mutuamente exclusivos — a regra dos 2 níveis (quem tem mãe não pode
 * ser mãe) garante isso no backend, então a tela nunca precisa oferecer o impossível.
 *
 * `codigoVinculo` vem para INDEPENDENTE e MAE; `mae` só para FILHA; `congregacoes` só para MAE.
 */
export interface VinculoStatus {
  estado: EstadoVinculo
  codigoVinculo: string | null
  /** Permite a tela dizer "gerado em ..." e sugerir rotação — o código não expira. */
  codigoGeradoEm: string | null
  mae: IgrejaResumo | null
  congregacoes: IgrejaResumo[]
}

export interface MembrosConsolidado {
  total: number
  ativos: number
  inativos: number
  visitantes: number
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
  membros: MembrosConsolidado
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
