'use client'

import { useEffect, useSyncExternalStore } from 'react'
import { useRouter } from 'next/navigation'
import { useAuthStore } from '@/store/authStore'
import type { Role } from '@/types/usuario.types'

const subscribe = () => () => {}

function useHidratado() {
  return useSyncExternalStore(
    subscribe,
    () => true,   
    () => false,  
  )
}

export function RoleGuard({
  roles,
  children,
}: {
  roles: Role[]
  children: React.ReactNode
}) {
  const router = useRouter()
  const role = useAuthStore((state) => state.role)
  const hidratado = useHidratado()

  const autorizado = role ? roles.includes(role) : false

  useEffect(() => {
    if (hidratado && !autorizado) {
      router.replace('/inicio')
    }
  }, [hidratado, autorizado, router])

  if (!hidratado || !autorizado) return null

  return <>{children}</>
}