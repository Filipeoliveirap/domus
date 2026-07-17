'use client'

import { WifiOff } from 'lucide-react'
import { useStatusRede } from '@/hooks/useStatusRede'
import styles from './FaixaOffline.module.css'

export function FaixaOffline() {
  const online = useStatusRede()

  if (online) return null

  return (
    <div className={styles.faixa} role="status" aria-live="polite">
      <WifiOff size={16} />
      <span>Você está sem conexão. Tentando reconectar…</span>
    </div>
  )
}