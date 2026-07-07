import type { TipoCategoria } from '@/types/financeiro/categoria.type'

export function rotuloTipoCategoria(tipo: TipoCategoria): string {
  const rotulos: Record<TipoCategoria, string> = {
    ENTRADA: 'Entrada',
    SAIDA: 'Saída',
    AMBOS: 'Ambos',
  }
  return rotulos[tipo]
}

export function varianteTipoCategoria(tipo: TipoCategoria): 'entrada' | 'saida' | 'ambos' {
  if (tipo === 'ENTRADA') return 'entrada'
  if (tipo === 'SAIDA') return 'saida'
  return 'ambos'
}