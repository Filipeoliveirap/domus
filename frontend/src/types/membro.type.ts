import type { Role } from './usuario.types'

export type StatusMembro = 'ATIVO' | 'INATIVO' | 'VISITANTE'
export type EstadoCivil = 'SOLTEIRO' | 'CASADO' | 'DIVORCIADO' | 'VIUVO'

export interface MembroRequest {
  nome: string
  email?: string
  telefone?: string
  dataNascimento?: string 
  endereco?: string
  status: StatusMembro
  estadoCivil?: EstadoCivil
  ministerio?: string
  observacoes?: string
}

export interface MembroResponse {
  id: string
  nome: string
  email: string | null
  telefone: string | null
  dataNascimento: string | null
  endereco: string | null
  status: StatusMembro
  estadoCivil: EstadoCivil | null
  ministerio: string | null
  foto: string | null
  observacoes: string | null
  createdAt: string
}

export interface ConcederAcessoRequest {
  membroId: string
  role: Role
  senha: string
}