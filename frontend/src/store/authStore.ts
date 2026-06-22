import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { Role } from '@/types/usuario.types'

interface AuthState {
  token: string | null
  id: string | null
  nome: string | null
  role: Role | null
  igrejaId: string | null
  isAuthenticated: boolean
  login: (data: { token: string; id: string; nome: string; role: Role; igrejaId: string }) => void
  logout: () => void
  atualizarUsuarioLogado: (data: Partial<Pick<AuthState, 'nome' | 'role'>>) => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      id: null,
      nome: null,
      role: null,
      igrejaId: null,
      isAuthenticated: false,
      login: (data) => {
        localStorage.setItem('domus:token', data.token)
        document.cookie = `domus:token=${data.token}; path=/; max-age=${60 * 60 * 24 * 7}`
        set({ ...data, isAuthenticated: true })
      },
      logout: () => {
        localStorage.removeItem('domus:token')
        document.cookie = 'domus:token=; path=/; max-age=0'
        set({ token: null, id: null, nome: null, role: null, igrejaId: null, isAuthenticated: false })
      },
      atualizarUsuarioLogado: (data) => set(data),
    }),
    { name: 'domus:auth' }
  )
)