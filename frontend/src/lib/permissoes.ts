import type { Role } from '@/types/usuario.types'

// Espelha os nomes de shared/security/Permissoes.java (grep acha as duas pontas). Não é autorização: só evita a UI oferecer o que o backend vai recusar.

const GESTORES: Role[] = ['ADMIN_IGREJA', 'LIDER']
const SO_ADMIN: Role[] = ['ADMIN_IGREJA']

function tem(role: Role | null | undefined, permitidos: Role[]): boolean {
  return role != null && permitidos.includes(role)
}

function temCapacidade(role: Role | null | undefined, capacidades: string[] | null | undefined, permitidos: Role[], capacidade: string): boolean {
  return tem(role, permitidos) || (capacidades != null && capacidades.includes(capacidade))
}

export const podeGerenciarInscricoes = (r: Role | null | undefined) => tem(r, GESTORES)
export const podeVerListaCompletaDeInscritos = (r: Role | null | undefined) => tem(r, GESTORES)

export function podeVerDadosSensiveisDePessoa(r: Role | null | undefined): boolean
export function podeVerDadosSensiveisDePessoa(r: Role | null | undefined, c: string[] | null | undefined): boolean
export function podeVerDadosSensiveisDePessoa(r: Role | null | undefined, c?: string[] | null | undefined): boolean {
  return c !== undefined ? temCapacidade(r, c, SO_ADMIN, 'SECRETARIO') : tem(r, SO_ADMIN)
}

export function podeGerenciarPessoas(r: Role | null | undefined): boolean
export function podeGerenciarPessoas(r: Role | null | undefined, c: string[] | null | undefined): boolean
export function podeGerenciarPessoas(r: Role | null | undefined, c?: string[] | null | undefined): boolean {
  return c !== undefined ? temCapacidade(r, c, SO_ADMIN, 'SECRETARIO') : tem(r, SO_ADMIN)
}

export const podeGerenciarEventos = (r: Role | null | undefined) => tem(r, GESTORES)

export function podeVerFinanceiro(r: Role | null | undefined): boolean
export function podeVerFinanceiro(r: Role | null | undefined, c: string[] | null | undefined): boolean
export function podeVerFinanceiro(r: Role | null | undefined, c?: string[] | null | undefined): boolean {
  return c !== undefined ? temCapacidade(r, c, SO_ADMIN, 'TESOUREIRO') : tem(r, SO_ADMIN)
}

export function podeVerUsuariosEFinanceiroNaBuscaGlobal(r: Role | null | undefined): boolean
export function podeVerUsuariosEFinanceiroNaBuscaGlobal(r: Role | null | undefined, c: string[] | null | undefined): boolean
export function podeVerUsuariosEFinanceiroNaBuscaGlobal(r: Role | null | undefined, c?: string[] | null | undefined): boolean {
  return c !== undefined ? temCapacidade(r, c, SO_ADMIN, 'TESOUREIRO') : tem(r, SO_ADMIN)
}

export const podeGerenciarUsuarios = (r: Role | null | undefined) => tem(r, SO_ADMIN)
export const podeGerenciarCadastroMinisterios = (r: Role | null | undefined) => tem(r, SO_ADMIN)

export function podeGerenciarVisitantes(r: Role | null | undefined): boolean
export function podeGerenciarVisitantes(r: Role | null | undefined, c: string[] | null | undefined): boolean
export function podeGerenciarVisitantes(r: Role | null | undefined, c?: string[] | null | undefined): boolean {
  return c !== undefined ? temCapacidade(r, c, SO_ADMIN, 'SECRETARIO') : tem(r, SO_ADMIN)
}

export const podeGerenciarCelulas = (r: Role | null | undefined) => tem(r, SO_ADMIN)
export const podeConectarContaPagamento = (r: Role | null | undefined) => tem(r, SO_ADMIN)
