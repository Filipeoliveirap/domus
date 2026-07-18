import type { EventoResumo } from './inicio.type'
import type { MovimentacaoResponse } from './financeiro/movimentacao.type'

export interface DashboardResponse {
  membros: { total: number; novosMes: number }
  eventos: { mes: number; semana: number }
  financeiro: { entradasMes: string; saidasMes: string; saldoMes: string }
  movimentacoesRecentes: MovimentacaoResponse[]
  proximosEventos: EventoResumo[]
}
