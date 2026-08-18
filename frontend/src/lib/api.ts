import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios'
import { queryClient } from '@/lib/queryClient'
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

// Token vive em cookie httpOnly; sem interceptor de request, o navegador e o axios (XSRF default) cuidam disso sozinhos.

// /auth/me fica de fora de propósito: se o access expirou mas o refresh é válido, o load deve renovar a sessão, não deslogar.
const rotasAuth = [Endpoints.auth.LOGIN, Endpoints.auth.REFRESH, Endpoints.auth.LOGOUT]

// Single-flight: 401s concorrentes esperam a mesma promessa em vez de refreshes paralelos (a rotação do backend invalidaria um ao outro).
let refreshPromise: Promise<void> | null = null

function encerrarSessao() {
  useAuthStore.getState().logout()
  // Hoje o redirect abaixo dá reload (window.location) e o cache morreria junto, mas
  // depender disso é frágil: se um dia virar navegação SPA, o cache vazaria entre sessões.
  queryClient.clear()
  if (typeof window !== 'undefined') {
    // Preserva o destino para o usuário voltar onde estava depois de entrar de novo.
    // Rotas públicas ficam de fora: não faz sentido "voltar" para o próprio /login.
    const { pathname, search } = window.location
    const ehPublica = ['/login', '/cadastro', '/forgot-password', '/reset-password', '/']
      .some((rota) => pathname === rota || pathname.startsWith(`${rota}/`))
    window.location.href = ehPublica
      ? '/login'
      : `/login?next=${encodeURIComponent(pathname + search)}`
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
