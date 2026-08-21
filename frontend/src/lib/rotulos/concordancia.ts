import type { Genero } from '@/types/igreja/igreja.type'

/** Cresce sob demanda — só as formas realmente usadas em textos existentes. */
const FORMAS: Record<string, { MASCULINO: string; FEMININO: string }> = {
  novo: { MASCULINO: 'Novo', FEMININO: 'Nova' },
  nenhum: { MASCULINO: 'Nenhum', FEMININO: 'Nenhuma' },
  o: { MASCULINO: 'o', FEMININO: 'a' },
  um: { MASCULINO: 'um', FEMININO: 'uma' },
}

export function concordar(genero: Genero, forma: keyof typeof FORMAS): string {
  return FORMAS[forma][genero]
}
