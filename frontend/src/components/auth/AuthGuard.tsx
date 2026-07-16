'use client'

import { useEffect, useState } from 'react'
import { usePathname, useRouter } from 'next/navigation'
import axios from 'axios'
import { useAuthStore } from '@/store/authStore'
import { authService } from '@/services/auth.service'
import { EstadoErro } from '@/components/common/EstadoErro/EstadoErro'

/**
 * Guard de autenticação da área logada (grupo de rotas `(app)`).
 *
 * Como o token vive em cookie httpOnly, o JS não consegue olhar e saber se há sessão —
 * então perguntamos ao servidor (`GET /auth/me`) uma vez, no load, e ele é a verdade.
 *
 * Distingue TRÊS estados que não podem ser confundidos:
 *  - **não autenticado** (401) → redireciona para `/login`, preservando o destino;
 *  - **falha de infra** (500, rede, timeout) → mostra erro com "tentar novamente". NÃO
 *    desloga: a sessão pode estar perfeitamente válida e o backend é que tossiu;
 *  - **autenticado mas sem permissão** → segue e deixa a página mostrar `AcessoRestrito`.
 */
export function AuthGuard({ children }: { children: React.ReactNode }) {
  const router = useRouter()
  const pathname = usePathname()
  const hidratado = useAuthStore((s) => s.hidratado)
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  const login = useAuthStore((s) => s.login)
  const setHidratado = useAuthStore((s) => s.setHidratado)
  const [falhaInfra, setFalhaInfra] = useState(false)
  const [tentativa, setTentativa] = useState(0)

  // (A limpeza das chaves órfãs do localStorage vive em LimpezaSessaoLegada, no root
  // layout: aqui ela nunca rodaria para quem abre o app e não chega a logar.)

  useEffect(() => {
    if (hidratado) return
    let cancelado = false

    authService
      .me()
      .then((sessao) => {
        if (!cancelado) login(sessao)
      })
      .catch((erro: unknown) => {
        if (cancelado) return
        // Só 401 significa "não há sessão". Qualquer outra coisa (500, rede, timeout) é
        // falha de infra: deslogar aqui expulsaria alguém com sessão válida.
        const semSessao = axios.isAxiosError(erro) && erro.response?.status === 401
        if (semSessao) {
          setHidratado()
        } else {
          setFalhaInfra(true)
        }
      })

    return () => {
      cancelado = true
    }
  }, [hidratado, login, setHidratado, tentativa])

  useEffect(() => {
    if (hidratado && !isAuthenticated) {
      // Preserva o destino: quem abre um link direto e está deslogado volta pra ele depois
      // de entrar, em vez de cair sempre no /inicio. A query vem do window (e não de
      // useSearchParams) porque este componente vive num layout: o hook forçaria toda a
      // área (app) a exigir um Suspense e a virar renderização dinâmica.
      const destino = pathname + window.location.search
      router.replace(`/login?next=${encodeURIComponent(destino)}`)
    }
  }, [hidratado, isAuthenticated, router, pathname])

  if (falhaInfra) {
    return (
      <EstadoErro
        titulo="Não foi possível verificar sua sessão"
        mensagem="Verifique sua conexão e tente novamente."
        aoTentarNovamente={() => {
          setFalhaInfra(false)
          setTentativa((t) => t + 1)
        }}
      />
    )
  }

  if (!hidratado || !isAuthenticated) return null

  return <>{children}</>
}
