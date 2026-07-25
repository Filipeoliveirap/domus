import type { Role } from '@/types/usuario.types'

/**
 * As mesmas perguntas de autorização do backend (`shared/security/Permissoes.java`,
 * enum `Perfil`), com os mesmos nomes de propósito: a mesma regra deve ser
 * procurável nos dois lados (um grep de `podeGerenciarEventos` acha as duas pontas).
 *
 * ⚠️ Isto NÃO é autorização — é só para a interface não oferecer o que vai falhar
 * no servidor (esconder um botão, desabilitar uma ação). Quem decide de verdade é
 * o backend, sempre: um botão escondido aqui continua bloqueado lá, e uma falha
 * aqui (bug, cache velho de role) nunca vira acesso indevido — só uma UI que
 * oferece algo que o servidor vai recusar.
 */

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
