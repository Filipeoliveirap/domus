'use client'

import { Church } from 'lucide-react'
import { useAuthStore } from '@/store/authStore'
import { BuscaGlobal } from './busca/BuscaGlobal'
import styles from './TopBar.module.css'

export function TopBar() {
  const igrejaNome = useAuthStore((state) => state.igrejaNome)

  return (
    <header className={styles.topbar}>
      <BuscaGlobal />

      <div className={styles.igreja}>
        <div className={styles.igrejaIcone}>
          <Church size={18} />
        </div>
        <span className={styles.igrejaNome}>{igrejaNome ?? 'Minha Igreja'}</span>
      </div>
    </header>
  )
}