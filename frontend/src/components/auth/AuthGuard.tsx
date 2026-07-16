'use client'

import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { useAuthStore } from '@/store/authStore'

/**
 * Guard de autenticação da área logada (grupo de rotas `(app)`).
 *
 * Distingue dois estados que as páginas confundiam:
 *  - **não autenticado** (logout ou sessão ausente) → redireciona para `/login`;
 *  - **autenticado mas sem permissão** → segue e deixa a página mostrar `AcessoRestrito`.
 *
 * No logout, `isAuthenticated` vira `false` e este guard para de renderizar a área
 * protegida (retorna `null`) antes que qualquer página pinte o "Acesso negado" — isso
 * elimina o flash de `AcessoRestrito` que acontecia na corrida entre limpar a sessão
 * e a navegação para o login.
 *
 * Enquanto o store ainda não hidratou, renderiza os filhos normalmente: cada página
 * já trata o estado "não hidratado" com seus próprios skeletons.
 */
export function AuthGuard({ children }: { children: React.ReactNode }) {
  const router = useRouter()
  const hidratado = useAuthStore((s) => s.hidratado)
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)

  useEffect(() => {
    if (hidratado && !isAuthenticated) {
      router.replace('/login')
    }
  }, [hidratado, isAuthenticated, router])

  if (hidratado && !isAuthenticated) return null

  return <>{children}</>
}
