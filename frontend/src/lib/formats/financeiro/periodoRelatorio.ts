import type { PeriodoRelatorio } from '@/types/financeiro/relatorio.type'

export type PresetPeriodo = 'ESTE_MES' | 'MES_ANTERIOR' | 'ULTIMOS_3_MESES' | 'ULTIMOS_6_MESES' | 'ESTE_ANO'

function iso(data: Date): string {
  const ano = data.getFullYear()
  const mes = String(data.getMonth() + 1).padStart(2, '0')
  const dia = String(data.getDate()).padStart(2, '0')
  return `${ano}-${mes}-${dia}`
}

export function calcularPeriodo(preset: PresetPeriodo): PeriodoRelatorio {
  const hoje = new Date()
  const ano = hoje.getFullYear()
  const mes = hoje.getMonth()   

  switch (preset) {
    case 'ESTE_MES': {
      return { dataInicio: iso(new Date(ano, mes, 1)), dataFim: iso(new Date(ano, mes + 1, 0)) }
    }
    case 'MES_ANTERIOR': {
      return { dataInicio: iso(new Date(ano, mes - 1, 1)), dataFim: iso(new Date(ano, mes, 0)) }
    }
    case 'ULTIMOS_3_MESES': {
      return { dataInicio: iso(new Date(ano, mes - 2, 1)), dataFim: iso(new Date(ano, mes + 1, 0)) }
    }
    case 'ULTIMOS_6_MESES': {
      return { dataInicio: iso(new Date(ano, mes - 5, 1)), dataFim: iso(new Date(ano, mes + 1, 0)) }
    }
    case 'ESTE_ANO': {
      return { dataInicio: iso(new Date(ano, 0, 1)), dataFim: iso(new Date(ano, 11, 31)) }
    }
  }
}

export const ROTULOS_PRESET: Record<PresetPeriodo, string> = {
  ESTE_MES: 'Este mês',
  MES_ANTERIOR: 'Mês anterior',
  ULTIMOS_3_MESES: 'Últimos 3 meses',
  ULTIMOS_6_MESES: 'Últimos 6 meses',
  ESTE_ANO: 'Este ano',
}