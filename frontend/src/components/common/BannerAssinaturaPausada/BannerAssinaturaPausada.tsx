'use client'

import React from 'react'
import Link from 'next/link'
import { useAuthStore } from '@/store/authStore'
import { podeGerenciarAssinatura } from '@/lib/permissoes'

export function BannerAssinaturaPausada() {
  const statusAssinatura = useAuthStore((s) => s.statusAssinatura)
  const role = useAuthStore((s) => s.role)
  const ehAdmin = podeGerenciarAssinatura(role)

  if (statusAssinatura !== 'PAUSADA') {
    return null
  }

  return (
    <div className="bg-amber-950/90 border-b border-amber-800/80 px-4 py-3 text-amber-200 text-xs sm:text-sm flex flex-col sm:flex-row items-center justify-between gap-3 shadow-md z-40">
      <div className="flex items-center gap-2">
        <span className="text-base">⚠️</span>
        <div>
          <strong className="font-semibold text-amber-100">Assinatura Pausada por Pendência de Pagamento.</strong>
          <span className="ml-1 text-amber-200/90">
            O sistema está operando em modo de leitura apenas. Novas edições ou cadastros estão bloqueados.
          </span>
        </div>
      </div>

      {ehAdmin && (
        <Link
          href="/configuracoes/assinatura"
          className="bg-amber-500 hover:bg-amber-400 text-slate-950 font-bold px-3 py-1.5 rounded-lg text-xs transition shrink-0"
        >
          Atualizar Cartão
        </Link>
      )}
    </div>
  )
}
