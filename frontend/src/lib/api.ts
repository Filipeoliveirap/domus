import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios'
import * as Sentry from '@sentry/nextjs'
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

// Mesma lógica de single-flight, mas pro token CSRF (ver tratamento do 403 CSRF_INVALIDO abaixo).
let csrfRenovacaoPromise: Promise<void> | null = null

function encerrarSessao(motivo: 'refresh_falhou' | 'logout', urlOriginal?: string) {
  if (motivo === 'refresh_falhou') {
    // Instrumentação (BACKLOG "logout indevido ao falhar refresh"): sem repro conhecido
    // até 2026-08-20, esta é a pulga que fica — se voltar a acontecer com um usuário que
    // não devia ter sido deslogado, este evento no Sentry carrega a rota que disparou o
    // 401 original, pra investigar de verdade em vez de continuar só "observando".
    Sentry.captureMessage('Sessão encerrada por falha no refresh do token', {
      level: 'warning',
      tags: { origem: 'api.interceptor' },
      extra: { urlOriginal },
    })
  }
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
    const destino = ehPublica ? '/login' : `/login?next=${encodeURIComponent(pathname + search)}`
    // Já estamos lá: recarregar de novo não muda nada e, se algo nesta própria rota
    // pública também chamar uma rota autenticada (ex.: /login checando /auth/me pra
    // pular o formulário se já tiver sessão), vira reload infinito sem esta guarda.
    if (destino === pathname) return
    window.location.href = destino
  }
}

// O servidor reemite os cookies na resposta; o front não vê nem toca em token nenhum.
async function renovarAccessToken(): Promise<void> {
  await api.post(Endpoints.auth.REFRESH)
}

// GET qualquer (mesmo 401) já grava um XSRF-TOKEN novo — mesmo mecanismo de
// garantirCsrfCookie() do auth.service.ts, reaproveitado aqui pro caso pós-login.
async function renovarTokenCsrf(): Promise<void> {
  await api.get(Endpoints.auth.ME).catch(() => undefined)
}

type RequestComRetry = InternalAxiosRequestConfig & { _retry?: boolean; _retryCsrf?: boolean }

// Interceptor de response:
// - 401: tenta renovar o access token uma vez e reenvia a requisição original. Se o
//   refresh falhar, encerra a sessão de verdade.
// - 403 com codigo=CSRF_INVALIDO: busca um XSRF-TOKEN novo e reenvia uma vez. Um 403
//   ACESSO_NEGADO (negação de role de verdade) NÃO cai aqui — ver SecurityConfig no back,
//   que distingue os dois casos pelo tipo da exceção.
api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<{ error?: string }>) => {
    const original = error.config as RequestComRetry | undefined
    const status = error.response?.status
    const url = original?.url ?? ''
    const ehRotaAuth = rotasAuth.some((rota) => url.includes(rota))

    if (status === 403 && original && error.response?.data?.error === 'CSRF_INVALIDO') {
      if (original._retryCsrf) {
        return Promise.reject(error)
      }
      original._retryCsrf = true
      if (!csrfRenovacaoPromise) {
        csrfRenovacaoPromise = renovarTokenCsrf().finally(() => {
          csrfRenovacaoPromise = null
        })
      }
      await csrfRenovacaoPromise
      return api(original)
    }

    if (status !== 401 || !original || ehRotaAuth) {
      return Promise.reject(error)
    }

    if (original._retry) {
      encerrarSessao('refresh_falhou', url)
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
      encerrarSessao('refresh_falhou', url)
      return Promise.reject(error)
    }
  }
)
