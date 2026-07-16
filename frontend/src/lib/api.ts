import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios'
import { useAuthStore } from '@/store/authStore'
import { Endpoints } from '@/lib/endpoints'

export const api = axios.create({
  baseURL: process.env.NEXT_PUBLIC_API_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  // Cookies same-origin já iriam de qualquer forma; explícito porque a sessão depende disso.
  withCredentials: true,
})

// Não há interceptor de request: o token vive em cookie httpOnly e o navegador o envia
// sozinho. O header X-XSRF-TOKEN do CSRF também é automático — os defaults do axios já são
// xsrfCookieName 'XSRF-TOKEN' e xsrfHeaderName 'X-XSRF-TOKEN', e como o proxy nos deixa
// same-origin ele faz isso sem configuração.

// Endpoints de auth que NÃO devem disparar uma tentativa de refresh ao receber 401.
// /auth/me NÃO entra aqui de propósito: se o access expirou mas o refresh é válido,
// queremos justamente que o load renove a sessão em vez de deslogar o usuário.
const rotasAuth = [Endpoints.auth.LOGIN, Endpoints.auth.REFRESH, Endpoints.auth.LOGOUT]

// Single-flight: um único refresh em andamento por vez. Requisições 401 concorrentes
// esperam nesta mesma promessa em vez de dispararem refreshes paralelos (que a rotação
// do backend invalidaria entre si).
let refreshPromise: Promise<void> | null = null

function encerrarSessao() {
  useAuthStore.getState().logout()
  if (typeof window !== 'undefined') {
    window.location.href = '/login'
  }
}

// O servidor reemite os cookies na resposta; o front não vê nem toca em token nenhum.
async function renovarAccessToken(): Promise<void> {
  await api.post(Endpoints.auth.REFRESH)
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
      await refreshPromise
      return api(original)
    } catch {
      // Sem sessão renovável: limpa o estado. Não chamamos /auth/logout aqui — o refresh
      // já está morto, e o cookie de access expira sozinho em 10 min.
      encerrarSessao()
      return Promise.reject(error)
    }
  }
)
