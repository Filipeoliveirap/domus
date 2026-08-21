import { create } from 'zustand'
import type { Role } from '@/types/usuario.types'
import type { Sessao } from '@/types/auth.types'
import type { RotulosCustomizados } from '@/types/igreja/igreja.type'

// Estado em memória, nunca localStorage: o cookie httpOnly pode expirar enquanto o localStorage seguiria afirmando sessão ativa.
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
  // true = logout explícito (evita reaproveitar o `next` da rota anterior e cair na tela de outro usuário).
  logoutIntencional: boolean
  exclusaoAgendadaEm: string | null
  diasRestantes: number | null
  precisaAceitarTermos: boolean
  termosAceitosEm: string | null
  rotulos: RotulosCustomizados | null
  login: (data: Sessao) => void
  logout: () => void
  atualizarUsuarioLogado: (data: Partial<Pick<AuthState, 'nome' | 'role' | 'fotoId' | 'cargo' | 'igrejaSigla' | 'igrejaLogoId' | 'rotulos'>>) => void
  atualizarExclusaoAgendada: (exclusaoAgendadaEm: string | null, diasRestantes: number | null) => void
  confirmarAceiteTermos: () => void
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
  exclusaoAgendadaEm: null,
  diasRestantes: null,
  precisaAceitarTermos: false,
  termosAceitosEm: null,
  rotulos: null,
} as const

export const useAuthStore = create<AuthState>()((set) => ({
  ...estadoDeslogado,
  hidratado: false,
  logoutIntencional: false,
  login: (data) =>
    set({ ...data, isAuthenticated: true, hidratado: true, logoutIntencional: false }),
  logout: () => set({ ...estadoDeslogado, hidratado: true, logoutIntencional: true }),
  atualizarUsuarioLogado: (data) => set(data),
  atualizarExclusaoAgendada: (exclusaoAgendadaEm, diasRestantes) => set({ exclusaoAgendadaEm, diasRestantes }),
  confirmarAceiteTermos: () => set({ precisaAceitarTermos: false, termosAceitosEm: new Date().toISOString() }),
  setHidratado: () => set({ hidratado: true }),
}))
