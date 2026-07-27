import { create } from 'zustand'
import type { Role } from '@/types/usuario.types'
import type { Sessao } from '@/types/auth.types'

/**
 * Estado da sessão — em MEMÓRIA, nunca em localStorage.
 *
 * Os tokens vivem em cookie httpOnly (o JS não os lê) e a verdade sobre a sessão é do
 * servidor: no load, o AuthGuard pergunta via GET /auth/me e popula este store.
 * Persistir isso no localStorage seria o front ADIVINHAR — o cookie pode ter expirado
 * enquanto o localStorage segue afirmando que há sessão.
 */
interface AuthState {
  id: string | null
  nome: string | null
  role: Role | null
  fotoId: string | null
  igrejaId: string | null
  igrejaNome: string | null
  cargo: string | null
  igrejaSigla: string | null
  igrejaLogoId: string | null
  capacidadesExtras: string[]
  isAuthenticated: boolean
  hidratado: boolean
  /**
   * true = a pessoa clicou em "sair", em vez de a sessão ter caído sozinha.
   *
   * <p>Existe por um bug real (2026-07-21): o `next` do login guardava a rota anterior, e o
   * AuthGuard o preenchia ao ver o store vazio logo após o logout. Quem entrasse em seguida
   * com OUTRO usuário caía na tela do anterior — e, se fosse restrita, batia em "acesso
   * restrito" sem entender por quê. O `next` serve a quem abre um link direto estando
   * deslogado; depois de sair de propósito não há destino a preservar.
   */
  logoutIntencional: boolean
  login: (data: Sessao) => void
  logout: () => void
  atualizarUsuarioLogado: (data: Partial<Pick<AuthState, 'nome' | 'role' | 'fotoId' | 'cargo' | 'igrejaSigla' | 'igrejaLogoId'>>) => void
  setHidratado: () => void
}

const estadoDeslogado = {
  id: null,
  nome: null,
  role: null,
  fotoId: null,
  igrejaId: null,
  igrejaNome: null,
  cargo: null,
  igrejaSigla: null,
  igrejaLogoId: null,
  capacidadesExtras: [] as string[],
  isAuthenticated: false,
} as const

export const useAuthStore = create<AuthState>()((set) => ({
  ...estadoDeslogado,
  hidratado: false,
  logoutIntencional: false,
  login: (data) =>
    set({ ...data, isAuthenticated: true, hidratado: true, logoutIntencional: false }),
  logout: () => set({ ...estadoDeslogado, hidratado: true, logoutIntencional: true }),
  atualizarUsuarioLogado: (data) => set(data),
  setHidratado: () => set({ hidratado: true }),
}))
