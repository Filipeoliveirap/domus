'use client'

import Image from 'next/image'
import { Church, Menu } from 'lucide-react'
import { useAuthStore } from '@/store/authStore'
import { useUiStore } from '@/store/uiStore'
import { BuscaGlobal } from './busca/BuscaGlobal'
import { urlFoto } from '@/lib/urlFoto'
import styles from './TopBar.module.css'

export function TopBar() {
  const igrejaNome = useAuthStore((state) => state.igrejaNome)
  const igrejaSigla = useAuthStore((state) => state.igrejaSigla)
  const igrejaLogoId = useAuthStore((state) => state.igrejaLogoId)
  const alternarNav = useUiStore((state) => state.alternarNav)

  return (
    <header className={styles.topbar}>
      <button type="button" className={styles.hamburger} onClick={alternarNav} aria-label="Abrir menu">
        <Menu size={22} />
      </button>

      <BuscaGlobal />

      <div className={styles.igreja}>
        <div className={styles.igrejaIcone}>
          {urlFoto(igrejaLogoId, 'THUMB') ? (
            <Image src={urlFoto(igrejaLogoId, 'THUMB')!} alt={igrejaNome ?? 'Igreja'} width={32} height={32} unoptimized className={styles.igrejaLogo} />
          ) : (
            <Church size={18} />
          )}
        </div>
        <span className={styles.igrejaNome}>{igrejaSigla ?? igrejaNome ?? 'Minha Igreja'}</span>
      </div>
    </header>
  )
}