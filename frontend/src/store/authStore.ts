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
        document.cookie = `domus:token=${data.token}; path=/; max-age=${60 * 60 * 24 * 7}`
        set({ ...data, isAuthenticated: true })
      },

      logout: () => {
        localStorage.removeItem('domus:token')
        document.cookie = 'domus:token=; path=/; max-age=0'
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
      name: 'domus:auth',
    }
  )
)