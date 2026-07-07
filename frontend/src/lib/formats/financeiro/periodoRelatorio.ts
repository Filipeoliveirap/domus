import type { PeriodoRelatorio } from '@/types/financeiro/relatorio.type'

export type PresetPeriodo = 'ESTE_MES' | 'MES_ANTERIOR' | 'ESTE_ANO'

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
      const inicio = new Date(ano, mes, 1)
      const fim = new Date(ano, mes + 1, 0)   
      return { dataInicio: iso(inicio), dataFim: iso(fim) }
    }
    case 'MES_ANTERIOR': {
      const inicio = new Date(ano, mes - 1, 1)
      const fim = new Date(ano, mes, 0)
      return { dataInicio: iso(inicio), dataFim: iso(fim) }
    }
    case 'ESTE_ANO': {
      const inicio = new Date(ano, 0, 1)      
      const fim = new Date(ano, 11, 31)      
      return { dataInicio: iso(inicio), dataFim: iso(fim) }
    }
  }
}

export const ROTULOS_PRESET: Record<PresetPeriodo, string> = {
  ESTE_MES: 'Este mês',
  MES_ANTERIOR: 'Mês anterior',
  ESTE_ANO: 'Este ano',
}