'use client'

import React from 'react'
import Link from 'next/link'
import { AlertTriangle } from 'lucide-react'
import { useAuthStore } from '@/store/authStore'
import { podeGerenciarAssinatura } from '@/lib/permissoes'
import styles from './BannerAssinaturaPausada.module.css'

export function BannerAssinaturaPausada() {
  const statusAssinatura = useAuthStore((s) => s.statusAssinatura)
  const role = useAuthStore((s) => s.role)
  const ehAdmin = podeGerenciarAssinatura(role)

  if (statusAssinatura !== 'PAUSADA') {
    return null
  }

  return (
    <div className={styles.banner} role="alert">
      <span className={styles.texto}>
        <AlertTriangle size={16} aria-hidden="true" />
        <span>
          <strong>Assinatura Pausada • Modo Somente Leitura.</strong> Contrate um plano para liberar edições e continuar utilizando o Domus.
        </span>
      </span>

      {ehAdmin && (
        <Link href="/planos" className={styles.botao}>
          Contratar Plano
        </Link>
      )}
    </div>
  )
}
