'use client'

import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { useAuthStore } from '@/store/authStore'
import { authService } from '@/services/auth.service'

/**
 * Guard de autenticação da área logada (grupo de rotas `(app)`).
 *
 * Como o token vive em cookie httpOnly, o JS não consegue olhar e saber se há sessão —
 * então perguntamos ao servidor (`GET /auth/me`) uma vez, no load, e ele é a verdade.
 *
 * Distingue dois estados que as páginas confundiam:
 *  - **não autenticado** (logout ou sessão ausente) → redireciona para `/login`;
 *  - **autenticado mas sem permissão** → segue e deixa a página mostrar `AcessoRestrito`.
 *
 * Enquanto a resposta do `/auth/me` não chega, nada é renderizado. Isso evita o flash de
 * `AcessoRestrito` que acontecia na corrida entre limpar a sessão e navegar para o login.
 */
export function AuthGuard({ children }: { children: React.ReactNode }) {
  const router = useRouter()
  const hidratado = useAuthStore((s) => s.hidratado)
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  const login = useAuthStore((s) => s.login)
  const setHidratado = useAuthStore((s) => s.setHidratado)

  // Limpeza única da migração: chaves órfãs da era do localStorage. Sem isso, token velho
  // fica apodrecendo na máquina de quem já usou o sistema.
  useEffect(() => {
    localStorage.removeItem('domus:token')
    localStorage.removeItem('domus:auth')
    document.cookie = 'domus:token=; path=/; max-age=0'
  }, [])

  useEffect(() => {
    if (hidratado) return
    let cancelado = false

    authService
      .me()
      .then((sessao) => {
        if (!cancelado) login(sessao)
      })
      .catch(() => {
        // 401 aqui não é erro, é resposta: "não há sessão".
        if (!cancelado) setHidratado()
      })

    return () => {
      cancelado = true
    }
  }, [hidratado, login, setHidratado])

  useEffect(() => {
    if (hidratado && !isAuthenticated) {
      router.replace('/login')
    }
  }, [hidratado, isAuthenticated, router])

  if (!hidratado || !isAuthenticated) return null

  return <>{children}</>
}
