import type { Genero } from '@/types/igreja/igreja.type'

/** Cresce sob demanda — só as formas realmente usadas em textos existentes. */
const FORMAS: Record<string, { MASCULINO: string; FEMININO: string }> = {
  novo: { MASCULINO: 'Novo', FEMININO: 'Nova' },
  nenhum: { MASCULINO: 'Nenhum', FEMININO: 'Nenhuma' },
  o: { MASCULINO: 'o', FEMININO: 'a' },
  um: { MASCULINO: 'um', FEMININO: 'uma' },
  arquivado: { MASCULINO: 'arquivado', FEMININO: 'arquivada' },
  arquivados: { MASCULINO: 'arquivados', FEMININO: 'arquivadas' },
  este: { MASCULINO: 'Este', FEMININO: 'Esta' },
  lo: { MASCULINO: 'lo', FEMININO: 'la' },
  removido: { MASCULINO: 'removido', FEMININO: 'removida' },
  vinculado: { MASCULINO: 'vinculado', FEMININO: 'vinculada' },
  vinculados: { MASCULINO: 'vinculados', FEMININO: 'vinculadas' },
  os: { MASCULINO: 'Os', FEMININO: 'As' },
  os_min: { MASCULINO: 'os', FEMININO: 'as' },
  com_os_demais: { MASCULINO: 'com os demais', FEMININO: 'com as demais' },
  com_outros: { MASCULINO: 'com outros', FEMININO: 'com outras' },
  seu: { MASCULINO: 'o seu', FEMININO: 'a sua' },
}

export function concordar(genero: Genero, forma: keyof typeof FORMAS): string {
  return FORMAS[forma][genero]
}
