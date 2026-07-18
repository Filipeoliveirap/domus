'use client'

import { Church, Menu } from 'lucide-react'
import { useAuthStore } from '@/store/authStore'
import { useUiStore } from '@/store/uiStore'
import { BuscaGlobal } from './busca/BuscaGlobal'
import styles from './TopBar.module.css'

export function TopBar() {
  const igrejaNome = useAuthStore((state) => state.igrejaNome)
  const alternarNav = useUiStore((state) => state.alternarNav)

  return (
    <header className={styles.topbar}>
      <button type="button" className={styles.hamburger} onClick={alternarNav} aria-label="Abrir menu">
        <Menu size={22} />
      </button>

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