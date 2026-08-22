import { useAuthStore } from '@/store/authStore'
import { concordar } from './concordancia'
import type { Genero } from '@/types/igreja/igreja.type'

interface RotuloModulo {
  singular: string
  plural: string
  genero: Genero
}

interface Rotulos {
  ministerio: RotuloModulo
  congregacao: RotuloModulo
  celula: RotuloModulo
}

// "Rede" (não "Ministério") é o padrão real hoje — herdado de rotulosMinisterio.ts,
// que este mecanismo substitui. Trocar aqui mudaria o texto visível de toda igreja
// que nunca customizou nada.
const PADRAO: Rotulos = {
  ministerio: { singular: 'Rede', plural: 'Redes', genero: 'FEMININO' },
  congregacao: { singular: 'Unidade', plural: 'Unidades', genero: 'FEMININO' },
  celula: { singular: 'Célula', plural: 'Células', genero: 'FEMININO' },
}

function resolver(
  singular: string | null | undefined, plural: string | null | undefined,
  genero: Genero | null | undefined, padrao: RotuloModulo,
): RotuloModulo {
  return singular && plural && genero ? { singular, plural, genero } : padrao
}

export function useRotulos() {
  const custom = useAuthStore((s) => s.rotulos)

  const rotulos: Rotulos = {
    ministerio: resolver(custom?.ministerioSingular, custom?.ministerioPlural, custom?.ministerioGenero, PADRAO.ministerio),
    congregacao: resolver(custom?.congregacaoSingular, custom?.congregacaoPlural, custom?.congregacaoGenero, PADRAO.congregacao),
    celula: resolver(custom?.celulaSingular, custom?.celulaPlural, custom?.celulaGenero, PADRAO.celula),
  }

  return { ...rotulos, concordar }
}
