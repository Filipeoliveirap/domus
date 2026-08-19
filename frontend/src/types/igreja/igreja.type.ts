import type { Endereco } from '@/types/pessoa.type'

/** Reusa o mesmo `Endereco` da pessoa — no backend as colunas são idênticas (V11 e V13). */
export interface IgrejaDetalhe {
  id: string
  nome: string
  razaoSocial: string | null
  cnpj: string | null
  denominacao: string | null
  sigla: string | null
  emailContato: string
  telefoneContato: string | null
  logoFotoId: string | null
  endereco: Endereco | null
  /** Alimentam o card "Logs de atividade". Nulos enquanto ninguém tiver editado. */
  atualizadoEm: string | null
  atualizadoPorNome: string | null
  /** Preenchidos só quando há exclusão agendada (carência de 10 dias). */
  exclusaoAgendadaEm: string | null
  diasRestantes: number | null
}

export interface AtualizarIgrejaRequest {
  nome: string
  razaoSocial?: string | null
  cnpj?: string | null
  denominacao?: string | null
  sigla?: string | null
  emailContato: string
  telefoneContato?: string | null
  logoFotoId?: string | null
  endereco?: Endereco | null
}
