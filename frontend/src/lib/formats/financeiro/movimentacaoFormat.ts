import type { TipoMovimentacao } from '@/types/financeiro/movimentacao.type'

export function formatarMoeda(valor: string | number): string {
  const num = typeof valor === 'string' ? parseFloat(valor) : valor
  return new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency: 'BRL',
  }).format(num)
}

export function parsearMoeda(texto: string): string {
  const limpo = texto
    .replace(/[^\d,]/g, '')     
    .replace(',', '.')         
  const num = parseFloat(limpo)
  return isNaN(num) ? '' : num.toFixed(2)
}

export function formatarData(iso: string): string {
  const [ano, mes, dia] = iso.split('T')[0].split('-')
  return `${dia}/${mes}/${ano}`
}

export function rotuloTipo(tipo: TipoMovimentacao): string {
  return tipo === 'ENTRADA' ? 'Entrada' : 'Saída'
}

export function varianteTipo(tipo: TipoMovimentacao): 'entrada' | 'saida' {
  return tipo === 'ENTRADA' ? 'entrada' : 'saida'
}

export function formatarValorDigitado(valorCanonico: string): string {
  if (!valorCanonico) return ''
  const num = parseFloat(valorCanonico)
  if (isNaN(num)) return ''
  return new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency: 'BRL',
  }).format(num)
}