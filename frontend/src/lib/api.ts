import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios'
import { useAuthStore } from '@/store/authStore'
import { Endpoints } from '@/lib/endpoints'
import type { TokenPair } from '@/types/auth.types'

export const api = axios.create({
  baseURL: process.env.NEXT_PUBLIC_API_URL,
  headers: {
    'Content-Type': 'application/json',
  },
})

// Interceptor de request — injeta o access token em toda requisição autenticada
api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().token
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// Endpoints de auth que NÃO devem disparar uma tentativa de refresh ao receber 401.
const rotasAuth = [Endpoints.auth.LOGIN, Endpoints.auth.REFRESH, Endpoints.auth.LOGOUT]

// Single-flight: um único refresh em andamento por vez. Requisições 401 concorrentes
// esperam nesta mesma promessa em vez de dispararem refreshes paralelos (que a rotação
// do backend invalidaria entre si).
let refreshPromise: Promise<string> | null = null

function encerrarSessao() {
  useAuthStore.getState().logout()
  if (typeof window !== 'undefined') {
    window.location.href = '/login'
  }
}

async function renovarAccessToken(): Promise<string> {
  const refreshToken = useAuthStore.getState().refreshToken
  if (!refreshToken) throw new Error('Sem refresh token')

  const { data } = await api.post<TokenPair>(Endpoints.auth.REFRESH, { refreshToken })
  useAuthStore.getState().setTokens({ token: data.token, refreshToken: data.refreshToken })
  return data.token
}

// Interceptor de response — no 401, tenta renovar o access token uma vez e reenvia a
// requisição original. Se o refresh falhar, encerra a sessão de verdade.
api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const original = error.config as (InternalAxiosRequestConfig & { _retry?: boolean }) | undefined
    const status = error.response?.status
    const url = original?.url ?? ''

    const ehRotaAuth = rotasAuth.some((rota) => url.includes(rota))

    if (status !== 401 || !original || ehRotaAuth) {
      return Promise.reject(error)
    }

    if (original._retry) {
      encerrarSessao()
      return Promise.reject(error)
    }
    original._retry = true

    try {
      if (!refreshPromise) {
        refreshPromise = renovarAccessToken().finally(() => {
          refreshPromise = null
        })
      }
      const novoToken = await refreshPromise
      original.headers.Authorization = `Bearer ${novoToken}`
      return api(original)
    } catch {
      encerrarSessao()
      return Promise.reject(error)
    }
  }
)
