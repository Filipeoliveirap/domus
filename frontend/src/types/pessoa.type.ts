import type { Role } from './usuario.types'

export type Vinculo = 'MEMBRO' | 'CONGREGANTE'
export type EstadoCivil = 'SOLTEIRO' | 'CASADO' | 'DIVORCIADO' | 'VIUVO'
// Só dois valores: o uso é restringir inscrição em evento ("encontro de mulheres"),
// não descrever identidade.
export type Sexo = 'HOMEM' | 'MULHER'

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
  sexo?: Sexo
  cargo?: string
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
  sexo: Sexo | null
  cargo: string | null
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