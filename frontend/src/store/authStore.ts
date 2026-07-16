import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { Role } from '@/types/usuario.types'

interface AuthState {
  token: string | null
  refreshToken: string | null
  id: string | null
  nome: string | null
  role: Role | null
  foto: string | null
  igrejaId: string | null
  igrejaNome: string | null
  isAuthenticated: boolean
  hidratado: boolean
  login: (data: { token: string; refreshToken: string; id: string; nome: string; role: Role; igrejaId: string; igrejaNome: string }) => void
  setTokens: (data: { token: string; refreshToken: string }) => void
  logout: () => void
  atualizarUsuarioLogado: (data: Partial<Pick<AuthState, 'nome' | 'role'>>) => void
  setHidratado: () => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      refreshToken: null,
      id: null,
      nome: null,
      role: null,
      foto: null,
      igrejaId: null,
      igrejaNome: null,
      isAuthenticated: false,
      hidratado: false,
      login: (data) => {
        localStorage.setItem('domus:token', data.token)
        document.cookie = `domus:token=${data.token}; path=/; max-age=${60 * 60 * 24 * 7}`
        set({ ...data, isAuthenticated: true })
      },
      setTokens: ({ token, refreshToken }) => {
        localStorage.setItem('domus:token', token)
        document.cookie = `domus:token=${token}; path=/; max-age=${60 * 60 * 24 * 7}`
        set({ token, refreshToken })
      },
      logout: () => {
        localStorage.removeItem('domus:token')
        document.cookie = 'domus:token=; path=/; max-age=0'
        set({ token: null, refreshToken: null, id: null, nome: null, role: null, foto: null, igrejaId: null, igrejaNome: null, isAuthenticated: false })
      },
      atualizarUsuarioLogado: (data) => set(data),
      setHidratado: () => set({ hidratado: true }),
    }),
    {
      name: 'domus:auth',
      onRehydrateStorage: () => (state) => {
        state?.setHidratado()
      },
    }
  )
)