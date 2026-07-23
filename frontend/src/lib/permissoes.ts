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

export const podeGerenciarInscricoes = (r: Role | null | undefined) => tem(r, GESTORES)
export const podeVerListaCompletaDeInscritos = (r: Role | null | undefined) => tem(r, GESTORES)
export const podeVerDadosSensiveisDePessoa = (r: Role | null | undefined) => tem(r, SO_ADMIN)
export const podeGerenciarPessoas = (r: Role | null | undefined) => tem(r, SO_ADMIN)
export const podeGerenciarEventos = (r: Role | null | undefined) => tem(r, GESTORES)
export const podeVerFinanceiro = (r: Role | null | undefined) => tem(r, SO_ADMIN)

// Sem equivalente explícito no backend listado na Task 1: gestão de usuários
// (conceder/alterar acesso) hoje é admin-only no front, na mesma régua de
// `podeVerUsuariosEFinanceiroNaBuscaGlobal` do backend. Adicionada aqui porque
// a página `/usuarios` fazia a comparação direta — sem capacidade nomeada ela
// ficaria escondida do grep de auditoria.
export const podeGerenciarUsuarios = (r: Role | null | undefined) => tem(r, SO_ADMIN)
