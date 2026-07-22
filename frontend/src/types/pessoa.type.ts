import type { Role } from './usuario.types'

export type Vinculo = 'MEMBRO' | 'CONGREGANTE'
export type EstadoCivil = 'SOLTEIRO' | 'CASADO' | 'DIVORCIADO' | 'VIUVO'

export interface Endereco {
  cep?: string
  logradouro?: string
  numero?: string
  complemento?: string
  bairro?: string
  cidade?: string
  uf?: string
}

export interface PessoaRequest {
  nome: string
  email?: string
  telefone?: string
  dataNascimento?: string
  endereco?: Endereco
  vinculo: Vinculo
  estadoCivil?: EstadoCivil
  ministerio?: string
  observacoes?: string
  dataBatismo?: string
  fotoId?: string | null
}

export interface PessoaResponse {
  id: string
  nome: string
  email: string | null
  telefone: string | null
  dataNascimento: string | null
  endereco: Endereco | null
  vinculo: Vinculo
  estadoCivil: EstadoCivil | null
  ministerio: string | null
  fotoId: string | null
  observacoes: string | null
  createdAt: string
  dataBatismo: string | null
}

export interface ConcederAcessoRequest {
  pessoaId: string
  role: Role
  email?: string
}