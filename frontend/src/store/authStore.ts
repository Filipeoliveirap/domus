import { create } from 'zustand'
import { persist } from 'zustand/middleware'

interface AuthState {
  token: string | null
  nome: string | null
  role: string | null
  igrejaId: string | null
  isAuthenticated: boolean
  login: (data: { token: string; nome: string; role: string; igrejaId: string }) => void
  logout: () => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      nome: null,
      role: null,
      igrejaId: null,
      isAuthenticated: false,

      login: (data) => {
        localStorage.setItem('domus:token', data.token)
        set({
          token: data.token,
          nome: data.nome,
          role: data.role,
          igrejaId: data.igrejaId,
          isAuthenticated: true,
        })
      },

      logout: () => {
        localStorage.removeItem('domus:token')
        set({
          token: null,
          nome: null,
          role: null,
          igrejaId: null,
          isAuthenticated: false,
        })
      },
    }),
    {
      name: 'domus:auth', // chave no localStorage
    }
  )
)