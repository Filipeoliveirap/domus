'use client'

import { useEffect, useState } from 'react'
import { usePathname, useRouter } from 'next/navigation'
import axios from 'axios'
import { useAuthStore } from '@/store/authStore'
import { authService } from '@/services/auth.service'
import { EstadoErro } from '@/components/common/EstadoErro/EstadoErro'
import { ModalReaceitarTermos } from '@/components/common/ModalReaceitarTermos/ModalReaceitarTermos'

export function AuthGuard({ children }: { children: React.ReactNode }) {
  const router = useRouter()
  const pathname = usePathname()
  const hidratado = useAuthStore((s) => s.hidratado)
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  const logoutIntencional = useAuthStore((s) => s.logoutIntencional)
  const login = useAuthStore((s) => s.login)
  const setHidratado = useAuthStore((s) => s.setHidratado)
  const precisaAceitarTermos = useAuthStore((s) => s.precisaAceitarTermos)
  const [falhaInfra, setFalhaInfra] = useState(false)
  const [tentativa, setTentativa] = useState(0)

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
      if (logoutIntencional) {
        router.replace('/login')
        return
      }

      // window.location (não useSearchParams) porque este componente é um layout —
      // o hook forçaria toda a área (app) a virar renderização dinâmica
      const destino = pathname + window.location.search
      router.replace(`/login?next=${encodeURIComponent(destino)}`)
    }
  }, [hidratado, isAuthenticated, logoutIntencional, router, pathname])

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

  if (precisaAceitarTermos) return <ModalReaceitarTermos />

  return <>{children}</>
}
