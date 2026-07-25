export interface VisitanteResponse {
  id: string
  nome: string
  telefone: string | null
  endereco: {
    cep?: string
    logradouro?: string
    numero?: string
    complemento?: string
    bairro?: string
    cidade?: string
    uf?: string
  } | null
  sexo: 'HOMEM' | 'MULHER' | null
  estadoCivil: 'SOLTEIRO' | 'CASADO' | 'DIVORCIADO' | 'VIUVO' | null
  dataNascimento: string | null
  temFilhos: boolean
  quantidadeFilhos: number | null
  observacoes: string | null
  contatoRealizado: boolean
  visitaRealizada: boolean
  acompanhamentoFeito: boolean
  createdAt: string
}

export interface VisitanteRequest {
  nome: string
  telefone?: string
  endereco?: {
    cep?: string
    logradouro?: string
    numero?: string
    complemento?: string
    bairro?: string
    cidade?: string
    uf?: string
  }
  sexo?: 'HOMEM' | 'MULHER'
  estadoCivil?: 'SOLTEIRO' | 'CASADO' | 'DIVORCIADO' | 'VIUVO'
  dataNascimento?: string
  temFilhos?: boolean
  quantidadeFilhos?: number
  observacoes?: string
}

export interface ToggleRequest {
  valor: boolean
}
