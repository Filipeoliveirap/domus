
'use client'

import { useAuthStore } from '@/store/authStore'
import type { Role } from '@/types/usuario.types'

export function useAutorizacao(rolesPermitidas: Role[]) {
  const role = useAuthStore((s) => s.role)
  const hidratado = useAuthStore((s) => s.hidratado)

  return {
    hidratado,
    autorizado: role != null && rolesPermitidas.includes(role),
  }
}