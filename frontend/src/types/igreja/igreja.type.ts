import type { Endereco } from '@/types/pessoa.type'

/** Reusa o mesmo `Endereco` da pessoa — no backend as colunas são idênticas (V11 e V13). */
export interface IgrejaDetalhe {
  id: string
  nome: string
  razaoSocial: string | null
  cnpj: string | null
  denominacao: string | null
  emailContato: string
  telefoneContato: string | null
  logoUrl: string | null
  endereco: Endereco | null
  /** Alimentam o card "Logs de atividade". Nulos enquanto ninguém tiver editado. */
  atualizadoEm: string | null
  atualizadoPorNome: string | null
}

export interface AtualizarIgrejaRequest {
  nome: string
  razaoSocial?: string | null
  cnpj?: string | null
  denominacao?: string | null
  emailContato: string
  telefoneContato?: string | null
  logoUrl?: string | null
  endereco?: Endereco | null
}
